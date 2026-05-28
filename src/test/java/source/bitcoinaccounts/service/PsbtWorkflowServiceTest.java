package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletEntity;
import source.bitcoinaccounts.model.ColdWalletUtxoEntity;
import source.bitcoinaccounts.model.PsbtWorkflowEntity;
import source.bitcoinaccounts.repository.ColdWalletUtxoRepository;
import source.bitcoinaccounts.repository.PsbtWorkflowRepository;
import source.common.service.AddressDerivationService;
import source.transactions.infra.BitcoinCoreRpcClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PsbtWorkflowServiceTest {

    @Test
    void submitSignedRejectsChangedInputsAndUnlocksSelectedUtxos() throws Exception {
        BitcoinAccountService accountService = mock(BitcoinAccountService.class);
        ColdWalletUtxoRepository utxoRepository = mock(ColdWalletUtxoRepository.class);
        PsbtWorkflowRepository workflowRepository = mock(PsbtWorkflowRepository.class);
        BitcoinAccountAuditService auditService = mock(BitcoinAccountAuditService.class);
        AddressDerivationService addressDerivationService = mock(AddressDerivationService.class);
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BitcoinCoreRpcClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bitcoinCore);

        PsbtWorkflowService service = new PsbtWorkflowService(
                accountService,
                utxoRepository,
                workflowRepository,
                auditService,
                addressDerivationService,
                provider,
                "regtest",
                5_000L,
                2,
                24);

        UUID walletId = UUID.randomUUID();
        PsbtWorkflowEntity workflow = new PsbtWorkflowEntity();
        workflow.setColdWalletId(walletId);
        workflow.setUnsignedPsbt("unsigned");
        workflow.setDestinationAddress("bcrt1qdestination0000000000000000000000");
        workflow.setAmountSats(20_000L);
        workflow.setSelectedInputsHash(hashOf("originaltx:0"));
        workflow.setSelectedOutpoints("originaltx:0");
        workflow.setDestinationOutputsHash(hashOf("bcrt1qdestination0000000000000000000000|20000"));
        workflow.setEstimatedFeeSats(1_000L);
        workflow.setExpiresAt(LocalDateTime.now().plusHours(1));
        workflow.setStatus(BitcoinAccountEnums.PsbtStatus.WAITING_EXTERNAL_SIGNATURE);

        ColdWalletEntity wallet = new ColdWalletEntity();
        wallet.setAccountId(UUID.randomUUID());
        wallet.setFingerprint("d34db33f");
        wallet.setDerivationPath("m/84'/1'/0'");

        ColdWalletUtxoEntity locked = new ColdWalletUtxoEntity();
        locked.setColdWalletId(walletId);
        locked.setTxid("originaltx");
        locked.setVout(0);
        locked.setAmountSats(25_000L);
        locked.setStatus(BitcoinAccountEnums.UtxoStatus.LOCKED);

        ObjectNode decoded = new ObjectMapper().createObjectNode();
        ObjectNode tx = decoded.putObject("tx");
        ObjectNode input = tx.putArray("vin").addObject();
        input.put("txid", "tamperedtx");
        input.put("vout", 0);

        when(workflowRepository.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(accountService.requireOwnedColdWallet(42L, walletId)).thenReturn(wallet);
        when(bitcoinCore.decodePsbt("signed")).thenReturn(decoded);
        when(utxoRepository.findByColdWalletIdAndTxidAndVout(walletId, "originaltx", 0))
                .thenReturn(Optional.of(locked));

        assertThrows(IllegalArgumentException.class,
                () -> service.submitSigned(42L, workflow.getId(), "signed", false));

        assertEquals(BitcoinAccountEnums.PsbtStatus.REJECTED_TAMPERED, workflow.getStatus());
        assertEquals(BitcoinAccountEnums.UtxoStatus.UNSPENT, locked.getStatus());
        verify(workflowRepository).save(workflow);
        verify(utxoRepository).save(locked);
    }

    @Test
    void submitSignedRejectsUnknownChangeWhenXpubCannotValidateIt() throws Exception {
        BitcoinAccountService accountService = mock(BitcoinAccountService.class);
        ColdWalletUtxoRepository utxoRepository = mock(ColdWalletUtxoRepository.class);
        PsbtWorkflowRepository workflowRepository = mock(PsbtWorkflowRepository.class);
        BitcoinAccountAuditService auditService = mock(BitcoinAccountAuditService.class);
        AddressDerivationService addressDerivationService = mock(AddressDerivationService.class);
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BitcoinCoreRpcClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bitcoinCore);

        PsbtWorkflowService service = new PsbtWorkflowService(
                accountService,
                utxoRepository,
                workflowRepository,
                auditService,
                addressDerivationService,
                provider,
                "regtest",
                5_000L,
                2,
                24);

        UUID walletId = UUID.randomUUID();
        PsbtWorkflowEntity workflow = workflow(walletId);
        ColdWalletEntity wallet = new ColdWalletEntity();
        wallet.setAccountId(UUID.randomUUID());
        wallet.setFingerprint("d34db33f");
        wallet.setDerivationPath("m/84'/1'/0'");

        ObjectNode decoded = validDecodedPsbt("originaltx", 0, workflow.getDestinationAddress(), "0.00020000");
        ObjectNode change = ((ArrayNode) decoded.path("tx").path("vout")).addObject();
        change.put("value", "0.00004000");
        change.putObject("scriptPubKey").put("address", "bcrt1qunknownchange000000000000000000000");
        decoded.put("fee", "0.00001000");

        when(workflowRepository.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(accountService.requireOwnedColdWallet(42L, walletId)).thenReturn(wallet);
        when(bitcoinCore.decodePsbt("signed")).thenReturn(decoded);

        assertThrows(IllegalArgumentException.class,
                () -> service.submitSigned(42L, workflow.getId(), "signed", false));

        assertEquals(BitcoinAccountEnums.PsbtStatus.REJECTED_POLICY, workflow.getStatus());
    }

    @Test
    void submitSignedRejectsPsbtWhenFeeCannotBeVerified() throws Exception {
        BitcoinAccountService accountService = mock(BitcoinAccountService.class);
        ColdWalletUtxoRepository utxoRepository = mock(ColdWalletUtxoRepository.class);
        PsbtWorkflowRepository workflowRepository = mock(PsbtWorkflowRepository.class);
        BitcoinAccountAuditService auditService = mock(BitcoinAccountAuditService.class);
        AddressDerivationService addressDerivationService = mock(AddressDerivationService.class);
        BitcoinCoreRpcClient bitcoinCore = mock(BitcoinCoreRpcClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BitcoinCoreRpcClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bitcoinCore);

        PsbtWorkflowService service = new PsbtWorkflowService(
                accountService,
                utxoRepository,
                workflowRepository,
                auditService,
                addressDerivationService,
                provider,
                "regtest",
                5_000L,
                2,
                24);

        UUID walletId = UUID.randomUUID();
        PsbtWorkflowEntity workflow = workflow(walletId);
        ColdWalletEntity wallet = new ColdWalletEntity();
        wallet.setAccountId(UUID.randomUUID());
        wallet.setFingerprint("d34db33f");
        wallet.setDerivationPath("m/84'/1'/0'");
        wallet.setXpub("tpub-public-only");

        ObjectNode decoded = validDecodedPsbt("originaltx", 0, workflow.getDestinationAddress(), "0.00020000");

        when(workflowRepository.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(accountService.requireOwnedColdWallet(42L, walletId)).thenReturn(wallet);
        when(bitcoinCore.decodePsbt("signed")).thenReturn(decoded);

        assertThrows(IllegalArgumentException.class,
                () -> service.submitSigned(42L, workflow.getId(), "signed", false));

        assertEquals(BitcoinAccountEnums.PsbtStatus.REJECTED_POLICY, workflow.getStatus());
    }

    private PsbtWorkflowEntity workflow(UUID walletId) throws Exception {
        PsbtWorkflowEntity workflow = new PsbtWorkflowEntity();
        workflow.setColdWalletId(walletId);
        workflow.setUnsignedPsbt("unsigned");
        workflow.setDestinationAddress("bcrt1qdestination0000000000000000000000");
        workflow.setAmountSats(20_000L);
        workflow.setSelectedInputsHash(hashOf("originaltx:0"));
        workflow.setSelectedOutpoints("originaltx:0");
        workflow.setDestinationOutputsHash(hashOf("bcrt1qdestination0000000000000000000000|20000"));
        workflow.setEstimatedFeeSats(1_000L);
        workflow.setExpiresAt(LocalDateTime.now().plusHours(1));
        workflow.setStatus(BitcoinAccountEnums.PsbtStatus.WAITING_EXTERNAL_SIGNATURE);
        return workflow;
    }

    private ObjectNode validDecodedPsbt(String txid, int vout, String destinationAddress, String destinationAmountBtc) {
        ObjectNode decoded = new ObjectMapper().createObjectNode();
        ObjectNode tx = decoded.putObject("tx");
        ObjectNode input = tx.putArray("vin").addObject();
        input.put("txid", txid);
        input.put("vout", vout);
        ObjectNode destination = tx.putArray("vout").addObject();
        destination.put("value", destinationAmountBtc);
        destination.putObject("scriptPubKey").put("address", destinationAddress);
        return decoded;
    }

    private String hashOf(String value) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
