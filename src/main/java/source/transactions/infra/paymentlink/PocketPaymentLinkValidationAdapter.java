package source.transactions.infra.paymentlink;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkValidationPort;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

@Component
public class PocketPaymentLinkValidationAdapter implements PaymentLinkValidationPort {

    private static final Pattern MAINNET_TXID = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");

    private final BlockchainClient blockchainClient;
    private final int requiredConfirmations;

    public PocketPaymentLinkValidationAdapter(
            BlockchainClient blockchainClient,
            @Value("${bitcoin.min-confirmations:3}") int requiredConfirmations,
            @Value("${bitcoin.mock-mode:false}") boolean bitcoinMockMode,
            @Value("${voucher.mock.accept-any-txid:false}") boolean voucherMockMode) {
        this.blockchainClient = blockchainClient;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    @Override
    public void validateConfirmedTransaction(PaymentLinkDTO paymentLink, String txid, String fromAddress) {
        if (txid == null || txid.isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction("Transacao nao e valida");
        }

        if (paymentLink == null || paymentLink.getDepositAddress() == null || paymentLink.getDepositAddress().isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Payment link does not have a locked deposit address.");
        }
        if (paymentLink.getAmountBtc() == null || paymentLink.getAmountBtc().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Payment link amount is invalid.");
        }

        if (!MAINNET_TXID.matcher(txid).matches()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "TXID Bitcoin invalido. Envie um txid hexadecimal de 64 caracteres.");
        }

        JsonNode transaction = blockchainClient.getRawTransaction(txid, true);
        if (transaction == null || transaction.isNull() || transaction.isMissingNode()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Transacao ainda nao encontrada na rede Bitcoin.");
        }

        int confirmations = transaction.path("confirmations").isNumber()
                ? transaction.path("confirmations").asInt()
                : 0;
        if (confirmations < requiredConfirmations) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Transacao encontrada, mas ainda possui " + confirmations
                            + " confirmacao(oes). Aguarde " + requiredConfirmations + ".");
        }

        if (transaction.path("replaced_by_txid").isTextual()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Transacao foi substituida na rede e nao pode liquidar este payment link.");
        }

        long expectedSats = btcToSats(paymentLink.getAmountBtc());
        long matchedOutputSats = findOutputSats(transaction, paymentLink.getDepositAddress());
        if (matchedOutputSats <= 0L) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Transacao nao paga o endereco esperado do payment link.");
        }

        boolean exactAmountRequired = paymentLink.getAmountLocked() == null || paymentLink.getAmountLocked();
        boolean amountMatches = exactAmountRequired
                ? matchedOutputSats == expectedSats
                : matchedOutputSats >= expectedSats;
        if (!amountMatches) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Transacao nao paga o valor esperado do payment link.");
        }
    }

    private long findOutputSats(JsonNode transaction, String expectedAddress) {
        JsonNode outputs = transaction.path("vout");
        if (!outputs.isArray()) {
            return 0L;
        }
        long total = 0L;
        for (JsonNode output : outputs) {
            if (outputPaysAddress(output, expectedAddress)) {
                total += outputValueSats(output);
            }
        }
        return total;
    }

    private boolean outputPaysAddress(JsonNode output, String expectedAddress) {
        if (expectedAddress.equals(output.path("scriptpubkey_address").asText(null))) {
            return true;
        }
        JsonNode scriptPubKey = output.path("scriptPubKey");
        if (expectedAddress.equals(scriptPubKey.path("address").asText(null))) {
            return true;
        }
        JsonNode addresses = scriptPubKey.path("addresses");
        if (addresses.isArray()) {
            for (JsonNode address : addresses) {
                if (expectedAddress.equals(address.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private long outputValueSats(JsonNode output) {
        JsonNode sats = output.path("value");
        if (sats.isIntegralNumber()) {
            return sats.asLong();
        }
        if (sats.isNumber()) {
            return btcToSats(sats.decimalValue());
        }
        return 0L;
    }

    private long btcToSats(BigDecimal amountBtc) {
        try {
            return amountBtc.multiply(SATS_PER_BTC)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction(
                    "Payment link amount uses invalid BTC precision.");
        }
    }
}
