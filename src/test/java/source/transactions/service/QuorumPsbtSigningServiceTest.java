package source.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.infra.BitcoinCoreRpcClient;

class QuorumPsbtSigningServiceTest {

    @Test
    void preflightCreatesFundedPsbtAndReturnsOnlyMetadata() {
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        QuorumPsbtSigningService service = service(bitcoinCore, restTemplate, 1, "https://signer-a", "signer-a");
        when(bitcoinCore.createFundedPsbt("tb1qdestination", 25_000L, 6))
                .thenReturn(new BitcoinCoreRpcClient.FundedPsbt("funded-psbt", 500L));

        QuorumPsbtSigningService.OnchainFundingPreflight preflight = service.preflight(
                new ExternalPaymentsCustodyPort.OnchainPreflightCommand(
                        1L,
                        10L,
                        "MAIN",
                        "tb1qdestination",
                        25_000L,
                        1_000L,
                        "idem-1"));

        assertEquals(500L, preflight.feeSats());
        assertEquals(64, preflight.psbtHash().length());
        assertEquals(1, preflight.configuredSignerCount());
        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void signerIdentityMismatchDoesNotEnterQuorum() {
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        QuorumPsbtSigningService service = service(bitcoinCore, restTemplate, 1, "https://signer-a", "signer-a");
        when(bitcoinCore.createFundedPsbt("tb1qdestination", 25_000L, 6))
                .thenReturn(new BitcoinCoreRpcClient.FundedPsbt("funded-psbt", 500L));
        when(restTemplate.postForEntity(eq("https://signer-a"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"signerId\":\"evil\",\"signedPsbt\":\"signed-psbt\"}"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.execute(command(1_000L)));

        assertTrue(exception.getMessage().contains("Quorum signing failed"));
        verify(bitcoinCore, never()).combinePsbt(anyList());
        verify(bitcoinCore, never()).sendRawTransaction(any());
    }

    @Test
    void insufficientConfiguredSignersFailsBeforeFunding() {
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        QuorumPsbtSigningService service = service(bitcoinCore, restTemplate, 2, "https://signer-a", "signer-a");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.execute(command(1_000L)));

        assertTrue(exception.getMessage().contains("requires 2 signers"));
        verify(bitcoinCore, never()).createFundedPsbt(any(), any(Long.class), any(Integer.class));
    }

    @Test
    void broadcastFailureBecomesAmbiguousWithoutRawPsbtPayload() {
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        QuorumPsbtSigningService service = service(bitcoinCore, restTemplate, 1, "https://signer-a", "signer-a");
        when(bitcoinCore.createFundedPsbt("tb1qdestination", 25_000L, 6))
                .thenReturn(new BitcoinCoreRpcClient.FundedPsbt("funded-psbt", 500L));
        when(restTemplate.postForEntity(eq("https://signer-a"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"signerId\":\"signer-a\",\"signedPsbt\":\"signed-psbt\"}"));
        when(bitcoinCore.combinePsbt(anyList())).thenReturn("combined-psbt");
        when(bitcoinCore.finalizePsbt("combined-psbt"))
                .thenReturn(new BitcoinCoreRpcClient.FinalizedPsbt("deadbeef", true));
        when(bitcoinCore.sendRawTransaction("deadbeef")).thenThrow(new RuntimeException("timeout"));

        ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous exception = assertThrows(
                ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous.class,
                () -> service.execute(command(1_000L)));

        assertTrue(exception.rawPayload().contains("\"status\":\"UNKNOWN\""));
        assertTrue(exception.rawPayload().contains("\"acceptedSigners\":[\"signer-a\"]"));
        assertTrue(exception.rawPayload().contains("combinedPsbtHash"));
        assertTrue(!exception.rawPayload().contains("combined-psbt"));
        assertTrue(!exception.rawPayload().contains("deadbeef"));
    }

    private QuorumPsbtSigningService service(
            BitcoinCoreRpcClient bitcoinCore,
            RestTemplate restTemplate,
            int requiredSignatures,
            String signerUrls,
            String signerIds) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("bitcoinCoreRpcClient", bitcoinCore);
        return new QuorumPsbtSigningService(
                beanFactory.getBeanProvider(BitcoinCoreRpcClient.class),
                restTemplate,
                new ObjectMapper(),
                mock(NetworkTransferEventService.class),
                requiredSignatures,
                6,
                signerUrls,
                "",
                signerIds,
                true);
    }

    private ExternalPaymentsCustodyPort.OnchainPaymentCommand command(long maxFeeSats) {
        return new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                1L,
                10L,
                "MAIN",
                "tb1qdestination",
                25_000L,
                maxFeeSats,
                "test",
                "idem-1",
                "auth");
    }
}
