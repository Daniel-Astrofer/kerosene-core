package source.ledger.dto;

import java.math.BigDecimal;

/**
 * DTO para processar transações entre carteiras.
 *
 * Suporta múltiplos formatos para sender e receiver:
 * - Username do usuário (ex: "what")
 * - ID da carteira (ex: "1", "2", etc)
 * - Hash/Endereço da carteira (ex: "1A1z7agoat7F9gq5...")
 *
 * Campos de segurança adicionados:
 * - idempotencyKey: UUID gerado pelo app para prevenção de double-spend.
 * - requestTimestamp: epoch ms para rejeitar replays de requisições antigas.
 */
public class TransactionDTO {

    private String sender; // Username, Wallet ID ou Address
    private String receiver; // Username, Wallet ID ou Address
    private BigDecimal amount;
    private String context;

    /**
     * UUID gerado pelo app antes de cada nova intenção de pagamento.
     * Se duas requisições chegarem com a mesma key, a segunda é descartada
     * e retorna o resultado da primeira (idempotência).
     */
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
        return identifier.matches("^(1|3|bc1)[a-zA-Z0-9]{25,62}$");
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
