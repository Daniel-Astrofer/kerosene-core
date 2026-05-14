package source.ledger.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransactionDTO Tests")
class TransactionDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Should create TransactionDTO with default constructor")
    void shouldCreateTransactionDTOWithDefaultConstructor() {
        TransactionDTO dto = new TransactionDTO();
        assertNotNull(dto);
    }

    @Test
    @DisplayName("Should set and get sender correctly")
    void shouldSetAndGetSender() {
        TransactionDTO dto = new TransactionDTO();
        String sender = "user123";
        
        dto.setSender(sender);
        
        assertEquals(sender, dto.getSender());
    }

    @Test
    @DisplayName("Should set and get receiver correctly")
    void shouldSetAndGetReceiver() {
        TransactionDTO dto = new TransactionDTO();
        String receiver = "user456";
        
        dto.setReceiver(receiver);
        
        assertEquals(receiver, dto.getReceiver());
    }

    @Test
    @DisplayName("Should set and get amount correctly")
    void shouldSetAndGetAmount() {
        TransactionDTO dto = new TransactionDTO();
        BigDecimal amount = new BigDecimal("100.50");
        
        dto.setAmount(amount);
        
        assertEquals(amount, dto.getAmount());
    }

    @Test
    @DisplayName("Should set and get context correctly")
    void shouldSetAndGetContext() {
        TransactionDTO dto = new TransactionDTO();
        String context = "Payment for services";
        
        dto.setContext(context);
        
        assertEquals(context, dto.getContext());
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        TransactionDTO dto = new TransactionDTO();
        
        dto.setSender(null);
        dto.setReceiver(null);
        dto.setAmount(null);
        dto.setContext(null);
        
        assertNull(dto.getSender());
        assertNull(dto.getReceiver());
        assertNull(dto.getAmount());
        assertNull(dto.getContext());
    }

    @Test
    @DisplayName("Should create complete transaction DTO")
    void shouldCreateCompleteTransactionDTO() {
        TransactionDTO dto = new TransactionDTO();
        
        dto.setSender("sender123");
        dto.setReceiver("receiver456");
        dto.setAmount(new BigDecimal("250.75"));
        dto.setIdempotencyKey("idem-complete");
        dto.setContext("Monthly payment");
        
        assertEquals("sender123", dto.getSender());
        assertEquals("receiver456", dto.getReceiver());
        assertEquals(new BigDecimal("250.75"), dto.getAmount());
        assertEquals("idem-complete", dto.getIdempotencyKey());
        assertEquals("Monthly payment", dto.getContext());
    }

    @Test
    @DisplayName("Should reject null, zero and negative amounts")
    void shouldRejectInvalidAmounts() {
        assertAmountInvalid(null);
        assertAmountInvalid(BigDecimal.ZERO);
        assertAmountInvalid(new BigDecimal("-0.00000001"));
    }

    @Test
    @DisplayName("Should reject amounts with more than BTC precision")
    void shouldRejectTooManyDecimalPlaces() {
        TransactionDTO dto = validTransaction();
        dto.setAmount(new BigDecimal("0.000000001"));

        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test
    @DisplayName("Should require sender receiver and idempotency key")
    void shouldRequireCriticalFields() {
        TransactionDTO dto = validTransaction();
        dto.setSender(" ");
        dto.setReceiver(null);
        dto.setIdempotencyKey("");

        Set<?> violations = validator.validate(dto);

        assertEquals(3, violations.size());
    }

    private void assertAmountInvalid(BigDecimal amount) {
        TransactionDTO dto = validTransaction();
        dto.setAmount(amount);

        assertFalse(validator.validate(dto).isEmpty());
    }

    private TransactionDTO validTransaction() {
        TransactionDTO dto = new TransactionDTO();
        dto.setSender("sender123");
        dto.setReceiver("receiver456");
        dto.setAmount(new BigDecimal("0.01000000"));
        dto.setIdempotencyKey("idem-valid");
        return dto;
    }
}
