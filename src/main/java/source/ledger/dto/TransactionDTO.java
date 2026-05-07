package source.ledger.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO para processar transações entre carteiras.
 *
 * Suporta múltiplos formatos para sender e receiver:
 * - Username do usuário (ex: "what")
 * - ID da carteira (ex: "1", "2", etc)
 * - Endereço blockchain da carteira (ex: "bc1q...")
 * - Hash público de destino da carteira (ex: SHA-256 hexadecimal exposto em payment requests)
 *
 * Campos de segurança adicionados:
 * - idempotencyKey: UUID gerado pelo app para prevenção de double-spend.
 * - requestTimestamp: epoch ms para rejeitar replays de requisições antigas.
 */
public class TransactionDTO {

    @NotBlank(message = "sender is required")
    private String sender; // Username, Wallet ID ou Address

    @NotBlank(message = "receiver is required")
    private String receiver; // Username, Wallet ID ou Address

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
    @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount")
    @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")
    private BigDecimal amount;
    private String context;

    /**
     * UUID gerado pelo app antes de cada nova intenção de pagamento.
     * Se duas requisições chegarem com a mesma key, a segunda é descartada
     * e retorna o resultado da primeira (idempotência).
     */
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 96, message = "idempotencyKey must have at most 96 characters")
    private String idempotencyKey;

    /**
     * Timestamp da requisição em milissegundos (System.currentTimeMillis()).
     * O servidor rejeita requisições com timestamp mais antigo que 2 minutos
     * (anti-replay: impede reenvio de pacotes capturados).
     */
    private Long requestTimestamp;

    /**
     * JSON string containing the WebAuthn/Passkey assertion for transaction
     * confirmation.
     * Required if passkey transaction authentication is enabled for the sender.
     */
    private String passkeyAssertionJson;

    /**
     * Plaintext passphrase or mnemocode fragment for confirmation.
     * Required for MULTISIG_2FA or SHAMIR accounts to authorize domestic movements.
     */
    private String confirmationPassphrase;

    /**
     * 6-digit TOTP code for extra security layer.
     */
    private String totpCode;

    public TransactionDTO() {
    }

    public TransactionDTO(String sender, String receiver, BigDecimal amount, String context) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.context = context;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(Long requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public String getPasskeyAssertionJson() {
        return passkeyAssertionJson;
    }

    public void setPasskeyAssertionJson(String passkeyAssertionJson) {
        this.passkeyAssertionJson = passkeyAssertionJson;
    }

    public boolean hasPasskeyAssertion() {
        return passkeyAssertionJson != null && !passkeyAssertionJson.isBlank();
    }

    public boolean hasConfirmationPassphrase() {
        return confirmationPassphrase != null && !confirmationPassphrase.isBlank();
    }

    public boolean hasTotpCode() {
        return totpCode != null && !totpCode.isBlank();
    }

    public String getConfirmationPassphrase() {
        return confirmationPassphrase;
    }

    public void setConfirmationPassphrase(String confirmationPassphrase) {
        this.confirmationPassphrase = confirmationPassphrase;
    }

    public String getTotpCode() {
        return totpCode;
    }

    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }

    /**
     * Verifica se o identifier é um ID numérico (wallet ID)
     */
    public static boolean isNumericId(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        return identifier.matches("^\\d+$");
    }

    /**
     * Verifica se o identifier é um endereço Bitcoin (começa com 1, 3 ou bc1)
     */
    public static boolean isBitcoinAddress(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        // Support Mainnet (1, 3, bc1) and Testnet (m, n, 2, tb1)
        return identifier.matches("^(1|3|bc1|m|n|2|tb1)[a-zA-Z0-9]{25,90}$");
    }

    /**
     * Determina o tipo de identifier
     * Retorna: "numeric" (ID), "address" (Bitcoin), ou "username"
     */
    public static String identifierType(String identifier) {
        if (isNumericId(identifier)) {
            return "numeric";
        }
        if (isBitcoinAddress(identifier)) {
            return "address";
        }
        return "username";
    }
}
