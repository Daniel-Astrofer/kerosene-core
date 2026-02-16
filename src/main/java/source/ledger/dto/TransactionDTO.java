package source.ledger.dto;

import java.math.BigDecimal;

/**
 * DTO para processar transações entre carteiras.
 *
 * Suporta múltiplos formatos para sender e receiver:
 * - Username do usuário (ex: "what")
 * - ID da carteira (ex: "1", "2", etc)
 * - Hash/Endereço da carteira (ex: "1A1z7agoat7F9gq5...")
 */
public class TransactionDTO {

    private String sender;      // Username, Wallet ID ou Address (se não autenticado)
    private String receiver;    // Username, Wallet ID ou Address
    private BigDecimal amount;
    private String context;

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
