package source.payments.service;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import source.auth.model.entity.UserDataBase;
import source.common.service.TickerService;
import source.payments.dto.PaymentQuoteRequest;
import source.payments.dto.PaymentQuoteResponse;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentIntentRepository;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.infra.MempoolClient;
import source.wallet.service.WalletCardProfile;
import source.wallet.service.WalletCardProfileService;
import source.wallet.service.WalletCardType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentQuoteServiceTest {

    private PaymentIntentRepository paymentIntentRepository;
    private ReceivingCapabilityService receivingCapabilityService;
    private PaymentQuoteService service;

    @BeforeEach
    void setUp() {
        paymentIntentRepository = mock(PaymentIntentRepository.class);
        receivingCapabilityService = mock(ReceivingCapabilityService.class);
        PaymentAuditService paymentAuditService = mock(PaymentAuditService.class);
        TickerService tickerService = mock(TickerService.class);
        MempoolClient mempoolClient = mock(MempoolClient.class);
        WalletCardProfileService walletCardProfileService = mock(WalletCardProfileService.class);

        when(paymentIntentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tickerService.getPrice("BRL")).thenReturn(new BigDecimal("500000.00"));
        when(mempoolClient.getRecommendedFees()).thenReturn(new MempoolClient.RecommendedFees(50, 40, 20, 10));
        when(walletCardProfileService.resolveProfile(1L)).thenReturn(new WalletCardProfile(
                WalletCardType.BRONZE,
                new BigDecimal("0.009"),
                new BigDecimal("0.009"),
                BigDecimal.ZERO));

        service = new PaymentQuoteService(
                paymentIntentRepository,
                receivingCapabilityService,
                paymentAuditService,
                new PaymentResponseMapper(),
                new PaymentStateMachine(),
                tickerService,
                mempoolClient,
                new ExternalPaymentsMath("testnet"),
                walletCardProfileService,
                120,
                60,
                225,
                new BigDecimal("0.009"));
    }

    @Test
    void senderPaysLightningKeepsReceiverAmountExactAndAddsFeesToDebit() {
        PaymentQuoteResponse quote = service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.LIGHTNING,
                PaymentEnums.FeeMode.SENDER_PAYS,
                "100.00",
                "BRL",
                "BTC",
                null,
                "lnbc1p" + "a".repeat(80),
                null));

        assertEquals(20_000L, quote.receiverAmountSats());
        assertEquals(60L, quote.networkFeeSats());
        assertEquals(180L, quote.keroseneFeeSats());
        assertEquals(20_240L, quote.totalDebitSats());
    }

    @Test
    void recipientPaysOnchainDiscountsFeesFromRequestedDebit() {
        PaymentQuoteResponse quote = service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.ONCHAIN,
                PaymentEnums.FeeMode.RECIPIENT_PAYS,
                "100.00",
                "BRL",
                "BTC",
                null,
                testnetAddress(),
                PaymentEnums.OnchainSpeed.FAST));

        assertEquals(20_000L, quote.totalDebitSats());
        assertEquals(11_250L, quote.networkFeeSats());
        assertEquals(180L, quote.keroseneFeeSats());
        assertEquals(8_570L, quote.receiverAmountSats());
    }

    @ParameterizedTest
    @EnumSource(PaymentEnums.OnchainSpeed.class)
    void senderPaysOnchainUsesSelectedSpeedForNetworkFee(PaymentEnums.OnchainSpeed speed) {
        PaymentQuoteResponse quote = service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.ONCHAIN,
                PaymentEnums.FeeMode.SENDER_PAYS,
                "100.00",
                "BRL",
                "BTC",
                null,
                testnetAddress(),
                speed));

        long expectedNetworkFee = switch (speed) {
            case ECONOMY -> 10L * 225L;
            case NORMAL -> 40L * 225L;
            case FAST -> 50L * 225L;
        };
        assertEquals(20_000L, quote.receiverAmountSats());
        assertEquals(expectedNetworkFee, quote.networkFeeSats());
        assertEquals(180L, quote.keroseneFeeSats());
        assertEquals(20_000L + expectedNetworkFee + 180L, quote.totalDebitSats());
    }

    @Test
    void recipientPaysLightningDiscountsRoutingAndPlatformFees() {
        PaymentQuoteResponse quote = service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.LIGHTNING,
                PaymentEnums.FeeMode.RECIPIENT_PAYS,
                "100.00",
                "BRL",
                "BTC",
                null,
                "lnbc1p" + "b".repeat(80),
                null));

        assertEquals(20_000L, quote.totalDebitSats());
        assertEquals(60L, quote.networkFeeSats());
        assertEquals(180L, quote.keroseneFeeSats());
        assertEquals(19_760L, quote.receiverAmountSats());
    }

    @Test
    void internalQuoteHasNoPlatformOrNetworkFees() {
        UserDataBase receiver = user(2L, "bob", true);
        when(receivingCapabilityService.resolveUser("@bob")).thenReturn(Optional.of(receiver));
        when(receivingCapabilityService.isActive(receiver)).thenReturn(true);

        PaymentQuoteResponse quote = service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.INTERNAL,
                PaymentEnums.FeeMode.SENDER_PAYS,
                "100.00",
                "BRL",
                "BTC",
                "@bob",
                null,
                null));

        assertEquals(20_000L, quote.receiverAmountSats());
        assertEquals(0L, quote.networkFeeSats());
        assertEquals(0L, quote.keroseneFeeSats());
        assertEquals(20_000L, quote.totalDebitSats());
    }

    @Test
    void onchainRejectsAmountBelowDustAfterFees() {
        PaymentException exception = assertThrows(PaymentException.class, () -> service.quote(1L, new PaymentQuoteRequest(
                PaymentEnums.PaymentRail.ONCHAIN,
                PaymentEnums.FeeMode.RECIPIENT_PAYS,
                "1.00",
                "BRL",
                "BTC",
                null,
                testnetAddress(),
                PaymentEnums.OnchainSpeed.ECONOMY)));

        assertEquals("PAYMENT_NET_AMOUNT_NEGATIVE", exception.getErrorCode());
    }

    private UserDataBase user(Long id, String username, boolean active) {
        UserDataBase user = new UserDataBase();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setIsActive(active);
        return user;
    }

    private String testnetAddress() {
        return SegwitAddress.fromHash(TestNet3Params.get(), new ECKey().getPubKeyHash()).toString();
    }
}
