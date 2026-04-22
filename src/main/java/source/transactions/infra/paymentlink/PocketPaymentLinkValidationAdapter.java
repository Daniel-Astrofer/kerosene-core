package source.transactions.infra.paymentlink;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkDescription;
import source.transactions.application.paymentlink.PaymentLinkValidationPort;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.infra.BlockchainClient;

import java.util.regex.Pattern;

@Component
public class PocketPaymentLinkValidationAdapter implements PaymentLinkValidationPort {

    private static final Logger log = LoggerFactory.getLogger(PocketPaymentLinkValidationAdapter.class);
    private static final Pattern MAINNET_TXID = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final BlockchainClient blockchainClient;
    private final int requiredConfirmations;
    private final boolean bitcoinMockMode;
    private final boolean voucherMockMode;

    public PocketPaymentLinkValidationAdapter(
            BlockchainClient blockchainClient,
            @Value("${bitcoin.min-confirmations:3}") int requiredConfirmations,
            @Value("${bitcoin.mock-mode:false}") boolean bitcoinMockMode,
            @Value("${voucher.mock.accept-any-txid:false}") boolean voucherMockMode) {
        this.blockchainClient = blockchainClient;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.bitcoinMockMode = bitcoinMockMode;
        this.voucherMockMode = voucherMockMode;
    }

    @Override
    public void validateConfirmedTransaction(PaymentLinkDTO paymentLink, String txid, String fromAddress) {
        if (txid == null || txid.isBlank()) {
            throw new PaymentLinkExceptions.InvalidPaymentLinkTransaction("Transacao nao e valida");
        }

        if (isAllowedMockVoucher(paymentLink) || bitcoinMockMode) {
            log.warn("[PaymentLink] Mock transaction accepted for link {} while mock mode is enabled.",
                    paymentLink.getId());
            return;
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
    }

    private boolean isAllowedMockVoucher(PaymentLinkDTO paymentLink) {
        if (!voucherMockMode || paymentLink == null || paymentLink.getDescription() == null) {
            return false;
        }

        return PaymentLinkDescription.ONBOARDING_VOUCHER.equals(paymentLink.getDescription());
    }
}
