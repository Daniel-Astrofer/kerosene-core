package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.fasterxml.jackson.databind.ObjectMapper;
import source.ledger.service.LedgerService;
import source.transactions.application.paymentlink.PaymentLinkAddressAllocationPort;
import source.transactions.application.paymentlink.PaymentLinkWalletPort;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainClient;
import source.transactions.repository.PaymentLinkRepository;

import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.application.port.in.WalletAddressIndexPort;
import source.wallet.service.WalletCardProfileService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class PaymentLinkServiceRedisTest {

    @TestConfiguration
    static class WalletLookupTestConfig {
        @Bean
        @Primary
        WalletLookupPort walletLookupPort() {
            return mock(WalletLookupPort.class);
        }
    }

    @MockBean(name = "lndLightningGateway")
    private source.transactions.infra.LightningClient lndLightningGateway;

    @Autowired
    private PaymentLinkService paymentLinkService;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @MockBean
    private LedgerService ledgerService;

    @Autowired
    private WalletLookupPort walletLookupPort;

    @MockBean
    private PaymentLinkWalletPort paymentLinkWalletPort;

    @MockBean
    private WalletAddressIndexPort walletAddressIndexPort;

    @MockBean
    private PaymentLinkAddressAllocationPort paymentLinkAddressAllocationPort;

    @MockBean
    private WalletCardProfileService walletCardProfileService;

    @MockBean
    private BlockchainClient blockchainClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        paymentLinkRepository.deleteAll();
    }

    /**
     * Testa se um payment link e armazenado no banco duravel.
     */
    @Test
    public void testPaymentLinkStoredDurably() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Depósito de teste";
        stubPrimaryWalletAllocation(userId, 501L, "bc1quserlink1");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));

        assertNotNull(createdLink);
        assertEquals("pending", createdLink.getStatus());

        assertTrue(paymentLinkRepository.findById(createdLink.getId()).isPresent());
        assertEquals(description, paymentLinkRepository.findById(createdLink.getId()).orElseThrow().getDescription());
    }

    /**
     * Testa se o payment link e recuperado do store duravel.
     */
    @Test
    public void testPaymentLinkRetrievedFromRedis() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste durable";
        stubPrimaryWalletAllocation(userId, 502L, "bc1quserlink2");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));
        String linkId = createdLink.getId();

        PaymentLinkDTO retrievedLink = paymentLinkService.getPaymentLink(linkId);

        assertNotNull(retrievedLink);
        assertEquals(createdLink.getId(), retrievedLink.getId());
        assertEquals(0, amountBtc.compareTo(retrievedLink.getAmountBtc()));
    }

    /**
     * Testa o fluxo de confirmação e creditamento na carteira
     */
    @Test
    public void testConfirmPaymentCreditsWallet() {
        Long userId = 123L;
        BigDecimal amount = new BigDecimal("1.0");
        String description = "Credit Test";

        // Mock Wallet
        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setId(999L);
        when(walletCardProfileService.calculateDepositFee(eq(userId), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.00900000"));
        stubPrimaryWalletAllocation(userId, 999L, "bc1qcreditwallet");

        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amount, description, null, null, null, null, null, null));
        String txid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String fromAddress = "addr_from";
        stubBlockchainPayment(txid, "bc1qcreditwallet", 100000000L, 3);

        PaymentLinkDTO confirmed = paymentLinkService.confirmPayment(link.getId(), txid, fromAddress, "idem-credit");

        assertEquals("paid", confirmed.getStatus());
        assertEquals(txid, confirmed.getTxid());
        assertEquals(0, new BigDecimal("1.0").compareTo(confirmed.getGrossAmountBtc()));
        assertEquals(0, new BigDecimal("0.00900000").compareTo(confirmed.getDepositFeeBtc()));
        assertEquals(0, new BigDecimal("0.99100000").compareTo(confirmed.getNetAmountBtc()));
        verify(ledgerService, times(1)).updateBalance(eq(999L), eq(new BigDecimal("0.99100000")), contains("PAYMENT_LINK_"));
    }

    @Test
    public void testUserPaymentLinksReflectUpdatedPrimaryState() {
        Long userId = 777L;
        BigDecimal amount = new BigDecimal("0.25");

        WalletEntity mockWallet = new WalletEntity();
        mockWallet.setId(321L);
        when(walletCardProfileService.calculateDepositFee(eq(userId), any(BigDecimal.class)))
                .thenReturn(BigDecimal.ZERO.setScale(8));
        stubPrimaryWalletAllocation(userId, 321L, "bc1qlistsync");

        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amount, "List sync test", null, null, null, null, null, null));
        String txid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        stubBlockchainPayment(txid, "bc1qlistsync", 25000000L, 3);
        paymentLinkService.confirmPayment(
                link.getId(),
                txid,
                "sender",
                "idem-sync");

        List<PaymentLinkDTO> paymentLinks = paymentLinkService.getUserPaymentLinks(userId);

        assertEquals(1, paymentLinks.size());
        assertEquals(link.getId(), paymentLinks.get(0).getId());
        assertEquals("paid", paymentLinks.get(0).getStatus());
        assertEquals(0, new BigDecimal("0.25").compareTo(paymentLinks.get(0).getGrossAmountBtc()));
        assertEquals(0, BigDecimal.ZERO.compareTo(paymentLinks.get(0).getDepositFeeBtc()));
        assertEquals(0, new BigDecimal("0.25").compareTo(paymentLinks.get(0).getNetAmountBtc()));
    }

    @Test
    public void testOnboardingPaymentGoesToVerifyingStateWithoutCreditingWallet() {
        PaymentLinkDTO link = paymentLinkService.createOnboardingPaymentLink(
                "signup-session-1",
                new BigDecimal("0.00022000"),
                PaymentLinkService.ONBOARDING_VOUCHER_DESCRIPTION);
        String txid = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
        stubBlockchainPayment(txid, link.getDepositAddress(), 22000L, 3);

        PaymentLinkDTO confirmed = paymentLinkService.confirmPublicOnboardingPayment(
                link.getId(),
                txid,
                "sender");

        assertEquals("verifying_onboarding", confirmed.getStatus());
        verify(ledgerService, never()).updateBalance(anyLong(), any(), anyString());
    }

    /**
     * Testa se a expiracao fica persistida junto com o link.
     */
    @Test
    public void testDurableExpirationTimestamp() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste TTL";
        stubPrimaryWalletAllocation(userId, 503L, "bc1qttlwallet");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));

        assertNotNull(createdLink.getExpiresAt());
        assertTrue(paymentLinkRepository.findById(createdLink.getId()).orElseThrow().getExpiresAt()
                .isAfter(createdLink.getCreatedAt()));
    }

    /**
     * Testa remocao manual do store duravel.
     */
    @Test
    public void testRemoveFromDurableStore() {
        Long userId = 1L;
        BigDecimal amountBtc = new BigDecimal("0.5");
        String description = "Teste remoção";
        stubPrimaryWalletAllocation(userId, 504L, "bc1qremovewallet");

        PaymentLinkDTO createdLink = paymentLinkService.createPaymentLink(userId, new source.transactions.dto.CreatePaymentLinkRequest(amountBtc, description, null, null, null, null, null, null));
        String linkId = createdLink.getId();
        assertTrue(paymentLinkRepository.findById(linkId).isPresent());

        paymentLinkService.removePaymentLink(linkId);

        assertTrue(paymentLinkRepository.findById(linkId).isEmpty());
    }

    private void stubPrimaryWalletAllocation(Long userId, Long walletId, String depositAddress) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(walletId);
        wallet.setName("PRIMARY");

        when(paymentLinkWalletPort.findPrimaryWallet(userId)).thenReturn(wallet);
        when(walletLookupPort.findPrimaryWallet(userId)).thenReturn(wallet);
        when(paymentLinkAddressAllocationPort.allocate(eq(userId), eq(wallet), anyString(), eq(true)))
                .thenReturn(new PaymentLinkAddressAllocationPort.Allocation(
                        depositAddress,
                        "allocation-" + walletId,
                        "KEROSENE_LOCAL",
                        false));
    }

    private void stubBlockchainPayment(String txid, String address, long valueSats, int confirmations) {
        try {
            when(blockchainClient.getRawTransaction(txid, true)).thenReturn(objectMapper.readTree("""
                    {
                      "confirmations": %d,
                      "vout": [
                        {
                          "scriptpubkey_address": "%s",
                          "value": %d
                        }
                      ]
                    }
                    """.formatted(confirmations, address, valueSats)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
