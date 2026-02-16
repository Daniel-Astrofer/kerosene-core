package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.SignedTransactionDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.infra.BlockchainInfoClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final BlockchainInfoClient blockchainInfo;
    private static final long AVERAGE_TX_SIZE_BYTES = 225; // Tamanho médio de uma TX

    public TransactionServiceImpl(BlockchainInfoClient blockchainInfo) {
        this.blockchainInfo = blockchainInfo;
    }

    @Override
    public EstimatedFeeDTO estimateFee(BigDecimal amount) {
        // Consultar taxas recomendadas da blockchain
        JsonNode fees = blockchainInfo.getRecommendedFees();
        
        Long fastSatPerByte = 50L;
        Long standardSatPerByte = 35L;
        Long slowSatPerByte = 15L;
        
        if (fees != null) {
            fastSatPerByte = fees.has("fastestFee") ? fees.get("fastestFee").asLong(50L) : 50L;
            standardSatPerByte = fees.has("halfHourFee") ? fees.get("halfHourFee").asLong(35L) : 35L;
            slowSatPerByte = fees.has("hourFee") ? fees.get("hourFee").asLong(15L) : 15L;
        }

        // Calcular taxas em satoshis (tamanho da TX × taxa por byte)
        Long fastTotalSats = fastSatPerByte * AVERAGE_TX_SIZE_BYTES;
        Long standardTotalSats = standardSatPerByte * AVERAGE_TX_SIZE_BYTES;
        Long slowTotalSats = slowSatPerByte * AVERAGE_TX_SIZE_BYTES;

        // Converter para BTC
        BigDecimal fastBtc = satoshisToBtc(fastTotalSats);
        BigDecimal standardBtc = satoshisToBtc(standardTotalSats);
        BigDecimal slowBtc = satoshisToBtc(slowTotalSats);

        // Calcular quanto o destinatário receberá (usando taxa padrão como default)
        BigDecimal amountReceived = amount.subtract(standardBtc);
        BigDecimal totalToSend = amount.add(standardBtc);

        EstimatedFeeDTO estimate = new EstimatedFeeDTO(
                fastSatPerByte, standardSatPerByte, slowSatPerByte, amountReceived, totalToSend
        );
        estimate.setEstimatedFastBtc(fastBtc);
        estimate.setEstimatedStandardBtc(standardBtc);
        estimate.setEstimatedSlowBtc(slowBtc);

        return estimate;
    }

    @Override
    public TransactionResponseDTO sendTransaction(TransactionRequestDTO request) {
        BigDecimal totalAmount = request.getAmount();
        String from = request.getFromAddress();
        String to = request.getToAddress();
        Long feeSatoshis = request.getFeeSatoshis() != null ? request.getFeeSatoshis() : 0L;

        // Converter fee de satoshis para BTC
        BigDecimal feeBtc = satoshisToBtc(feeSatoshis);

        // Descontar a taxa do valor total
        BigDecimal amountReceived = totalAmount.subtract(feeBtc);

        // Validar que o valor final seja positivo
        if (amountReceived.compareTo(BigDecimal.ZERO) <= 0) {
            return new TransactionResponseDTO("error", "fee_exceeds_amount", feeSatoshis, BigDecimal.ZERO);
        }

        // Enviar apenas o valor final (já descontada a taxa)
        String txid = blockchainInfo.sendTransaction(from, to, amountReceived);

        return new TransactionResponseDTO(txid, "broadcasted", feeSatoshis, amountReceived);
    }

    @Override
    public TransactionResponseDTO getStatus(String txid) {
        JsonNode info = blockchainInfo.getTransactionInfo(txid);
        String status = "unknown";
        Long feeSats = 0L;
        if (info != null) {
            int confs = info.has("block_height") && !info.get("block_height").isNull() ? 1 : 0;
            if (confs > 0) status = "confirmed"; else status = "unconfirmed";
            if (info.has("fee")) {
                long feeSatoshis = info.get("fee").asLong(0L);
                feeSats = feeSatoshis;
            }
        }
        return new TransactionResponseDTO(txid, status, feeSats);
    }

    @Override
    public TransactionResponseDTO broadcastSignedTransaction(SignedTransactionDTO signedTx) {
        String txid = blockchainInfo.pushSignedTransaction(signedTx.getRawTxHex());
        if (txid == null || txid.isEmpty()) {
            return new TransactionResponseDTO("error", "failed", 0L);
        }
        return new TransactionResponseDTO(txid, "broadcasted", 0L);
    }

    private BigDecimal satoshisToBtc(Long satoshis) {
        return BigDecimal.valueOf(satoshis).divide(
                BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    }
}


