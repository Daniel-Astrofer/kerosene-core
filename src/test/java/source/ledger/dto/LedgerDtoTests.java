package source.ledger.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LedgerDtoTests {

    @Test
    void internalPaymentRequestDTOTests() {
        InternalPaymentRequestDTO dto = new InternalPaymentRequestDTO();
        dto.setId("id1");
        assertEquals("id1", dto.getId());

        dto.setRequesterUserId(123L);
        assertEquals(123L, dto.getRequesterUserId());

        dto.setReceiverWalletId(456L);
        assertEquals(456L, dto.getReceiverWalletId());

        dto.setReceiverWalletName("wallet1");
        assertEquals("wallet1", dto.getReceiverWalletName());

        dto.setDestinationHash("hash1");
        assertEquals("hash1", dto.getDestinationHash());

        dto.setAmount(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("100.00"), dto.getAmount());

        dto.setStatus("PENDING");
        assertEquals("PENDING", dto.getStatus());

        LocalDateTime now = LocalDateTime.now();
        dto.setExpiresAt(now);
        assertEquals(now, dto.getExpiresAt());

        dto.setCreatedAt(now);
        assertEquals(now, dto.getCreatedAt());

        dto.setPaidAt(now);
        assertEquals(now, dto.getPaidAt());
    }

    @Test
    void internalTransactionResponseDTOTests() {
        InternalTransactionResponseDTO dto = new InternalTransactionResponseDTO(
                "txid1", "status1", new BigDecimal("100.00"), "sender1", "receiver1", "context1"
        );
        assertEquals("txid1", dto.txid());
        assertEquals("status1", dto.status());
        assertEquals(new BigDecimal("100.00"), dto.amount());
        assertEquals("sender1", dto.sender());
        assertEquals("receiver1", dto.receiver());
        assertEquals("context1", dto.context());
    }

    @Test
    void ledgerDTOTests() {
        LedgerDTO dto = new LedgerDTO();
        dto.setId(1);
        assertEquals(1, dto.getId());

        dto.setWalletId(2L);
        assertEquals(2L, dto.getWalletId());

        dto.setWalletName("wallet2");
        assertEquals("wallet2", dto.getWalletName());

        dto.setBalance(new BigDecimal("200.00"));
        assertEquals(new BigDecimal("200.00"), dto.getBalance());

        dto.setNonce(3);
        assertEquals(3, dto.getNonce());

        dto.setLastHash("hash2");
        assertEquals("hash2", dto.getLastHash());

        dto.setContext("context2");
        assertEquals("context2", dto.getContext());

        dto.setAmount(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("50.00"), dto.getAmount());
    }

    @Test
    void paymentRequestPublicDTOTests() {
        InternalPaymentRequestDTO internal = new InternalPaymentRequestDTO();
        internal.setId("id3");
        internal.setAmount(new BigDecimal("300.00"));
        internal.setStatus("PAID");
        LocalDateTime now = LocalDateTime.now();
        internal.setExpiresAt(now);
        internal.setDestinationHash("hash3");

        PaymentRequestPublicDTO dto = new PaymentRequestPublicDTO(internal);
        assertEquals("id3", dto.getId());
        assertEquals(new BigDecimal("300.00"), dto.getAmount());
        assertEquals("PAID", dto.getStatus());
        assertEquals(now, dto.getExpiresAt());
        assertEquals("hash3", dto.getDestinationHash());
        assertTrue(dto.isLocked());

        dto.setId("id4");
        assertEquals("id4", dto.getId());

        dto.setAmount(new BigDecimal("400.00"));
        assertEquals(new BigDecimal("400.00"), dto.getAmount());

        dto.setStatus("PENDING");
        assertEquals("PENDING", dto.getStatus());

        dto.setExpiresAt(now.plusDays(1));
        assertEquals(now.plusDays(1), dto.getExpiresAt());

        dto.setDestinationHash("hash4");
        assertEquals("hash4", dto.getDestinationHash());

        dto.setLocked(false);
        assertFalse(dto.isLocked());
    }

    @Test
    void treasuryAuditConfigRequestDTOTests() {
        TreasuryAuditConfigRequestDTO dto = new TreasuryAuditConfigRequestDTO(
                new BigDecimal("500.00"), "xpub1"
        );
        assertEquals(new BigDecimal("500.00"), dto.maxWithdrawLimit());
        assertEquals("xpub1", dto.auditXpub());
    }

    @Test
    void treasuryAuditConfigResponseDTOTests() {
        LocalDateTime now = LocalDateTime.now();
        TreasuryAuditConfigResponseDTO dto = new TreasuryAuditConfigResponseDTO(
                new BigDecimal("600.00"), true, "xpub1 preview", now
        );
        assertEquals(new BigDecimal("600.00"), dto.maxWithdrawLimit());
        assertTrue(dto.auditXpubConfigured());
        assertEquals("xpub1 preview", dto.auditXpubPreview());
        assertEquals(now, dto.updatedAt());
    }
}
