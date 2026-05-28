package source.transactions.infra.paymentlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.model.PaymentLinkEntity;
import source.transactions.repository.PaymentLinkRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaPaymentLinkStoreTest {

    @Test
    void persistsPaymentLinkInDurableJpaStore() {
        PaymentLinkRepository repository = mock(PaymentLinkRepository.class);
        JpaPaymentLinkStore store = new JpaPaymentLinkStore(repository, new ObjectMapper());
        when(repository.findById("pay_test")).thenReturn(Optional.empty());
        when(repository.save(any(PaymentLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentLinkDTO dto = new PaymentLinkDTO();
        dto.setId("pay_test");
        dto.setUserId(42L);
        dto.setAmountBtc(new BigDecimal("0.00010000"));
        dto.setGrossAmountBtc(new BigDecimal("0.00010000"));
        dto.setDescription("invoice");
        dto.setDepositAddress("bc1qdurable");
        dto.setVisibility("PRIVATE");
        dto.setConfirmationMode("USER_ACTION_REQUIRED");
        dto.setAmountLocked(true);
        dto.setMetadata(Map.of("order", "123"));
        dto.setStatus("pending");
        dto.setCreatedAt(LocalDateTime.now());
        dto.setExpiresAt(LocalDateTime.now().plusHours(1));

        PaymentLinkDTO saved = store.save(dto);

        assertEquals("pay_test", saved.getId());
        ArgumentCaptor<PaymentLinkEntity> captor = ArgumentCaptor.forClass(PaymentLinkEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("pay_test", captor.getValue().getId());
        assertEquals(42L, captor.getValue().getUserId());
        assertTrue(captor.getValue().getMetadataJson().contains("order"));
    }
}
