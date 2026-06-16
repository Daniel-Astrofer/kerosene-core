package source.transactions.service;

import org.junit.jupiter.api.Test;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.transaction.BroadcastTransactionUseCase;
import source.transactions.application.transaction.CheckPendingTransactionsUseCase;
import source.transactions.application.transaction.CreateUnsignedTransactionUseCase;
import source.transactions.application.transaction.EstimateTransactionFeeUseCase;
import source.transactions.application.transaction.GetTransactionStatusUseCase;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.WithdrawRequestDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionServiceImplTest {

    @Test
    void withdrawConvertsNetworkFeeWithExactSatoshiPolicy() {
        ExternalPaymentsService externalPaymentsService = mock(ExternalPaymentsService.class);
        when(externalPaymentsService.sendOnchain(eq(42L), any(OnchainSendRequestDTO.class)))
                .thenReturn(responseWithFee(new BigDecimal("0.00000001")));

        TransactionServiceImpl service = service(externalPaymentsService);

        TransactionResponseDTO response = service.withdraw(42L, withdrawRequest());

        assertEquals(1L, response.getFeeSatoshis());
    }

    @Test
    void withdrawTreatsMissingNetworkFeeAsZeroSats() {
        ExternalPaymentsService externalPaymentsService = mock(ExternalPaymentsService.class);
        when(externalPaymentsService.sendOnchain(eq(42L), any(OnchainSendRequestDTO.class)))
                .thenReturn(responseWithFee(null));

        TransactionServiceImpl service = service(externalPaymentsService);

        TransactionResponseDTO response = service.withdraw(42L, withdrawRequest());

        assertEquals(0L, response.getFeeSatoshis());
    }

    private TransactionServiceImpl service(ExternalPaymentsService externalPaymentsService) {
        return new TransactionServiceImpl(
                mock(EstimateTransactionFeeUseCase.class),
                mock(CreateUnsignedTransactionUseCase.class),
                mock(GetTransactionStatusUseCase.class),
                mock(CheckPendingTransactionsUseCase.class),
                mock(BroadcastTransactionUseCase.class),
                externalPaymentsService,
                new ExternalPaymentsMath("testnet"));
    }

    private WithdrawRequestDTO withdrawRequest() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setIdempotencyKey("withdraw-test");
        request.setFromWalletName("MAIN");
        request.setToAddress("tb1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        request.setAmount(new BigDecimal("0.00100000"));
        request.setDescription("test withdraw");
        return request;
    }

    private ExternalTransferResponseDTO responseWithFee(BigDecimal networkFeeBtc) {
        return new ExternalTransferResponseDTO(
                UUID.randomUUID(),
                "ONCHAIN",
                "OUTBOUND_PAYMENT",
                "MEMPOOL",
                "KEROSENE_LOCAL",
                "MAIN",
                "tb1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                null,
                "txid-1",
                null,
                null,
                null,
                new BigDecimal("0.00100000"),
                networkFeeBtc,
                BigDecimal.ZERO.setScale(8),
                new BigDecimal("0.00100000"),
                "txid-1",
                0,
                null,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "test withdraw");
    }
}
