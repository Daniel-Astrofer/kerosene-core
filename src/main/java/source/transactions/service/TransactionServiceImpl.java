package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.infra.BlockchainInfoClient;
import source.transactions.model.PendingTransaction;
import source.transactions.repository.PendingTransactionRedisRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final BlockchainInfoClient blockchainInfo;
    private final PendingTransactionRedisRepository pendingTxRepository;
    private final BlockchainMonitorService monitorService;
    private static final long AVERAGE_TX_SIZE_BYTES = 225; // Tamanho médio de uma TX

    public TransactionServiceImpl(BlockchainInfoClient blockchainInfo,
            PendingTransactionRedisRepository pendingTxRepository,
            BlockchainMonitorService monitorService) {
        this.blockchainInfo = blockchainInfo;
        this.pendingTxRepository = pendingTxRepository;
        this.monitorService = monitorService;
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
                fastSatPerByte, standardSatPerByte, slowSatPerByte, amountReceived, totalToSend);
        estimate.setEstimatedFastBtc(fastBtc);
        estimate.setEstimatedStandardBtc(standardBtc);
        estimate.setEstimatedSlowBtc(slowBtc);

        return estimate;
    }

    @Override
    public UnsignedTransactionDTO createUnsignedTransaction(TransactionRequestDTO request) {
        // Gerar um txid temporário para rastreamento
        String tempTxId = "temp-" + UUID.randomUUID().toString();

        // Criar DTO com transação não assinada
        UnsignedTransactionDTO unsignedTx = new UnsignedTransactionDTO();
        unsignedTx.setTxId(tempTxId);
        unsignedTx.setFromAddress(request.getFromAddress());
        unsignedTx.setToAddress(request.getToAddress());
        unsignedTx.setTotalAmount(request.getAmount());
        unsignedTx.setFee(request.getFeeSatoshis());

        // Aqui você deve gerar a raw transaction hex usando bitcoinj ou similar
        // Por enquanto retornamos placeholder
        unsignedTx.setRawTxHex("RAW_TX_HEX_PLACEHOLDER");

        // Registrar no banco para monitoramento futuro (quando usuário fazer broadcast)
        // Não salvamos ainda porque não temos o txid real

        return unsignedTx;
    }

    @Override
    public TransactionResponseDTO getTransactionStatus(String txid) {
        // Primeiro verificar se temos no Redis
        PendingTransaction pending = monitorService.getTransaction(txid);

        if (pending != null) {
            return new TransactionResponseDTO(
                    txid,
                    pending.getStatus().toLowerCase(),
                    pending.getFeeSatoshis(),
                    pending.getAmount());
        }

        // Se não tiver no Redis, consultar blockchain diretamente
        JsonNode info = blockchainInfo.getTransactionInfo(txid);
        String status = "unknown";
        Long feeSats = 0L;

        if (info != null) {
            int confs = info.has("block_height") && !info.get("block_height").isNull() ? 1 : 0;
            if (confs > 0)
                status = "confirmed";
            else
                status = "unconfirmed";

            if (info.has("fee")) {
                feeSats = info.get("fee").asLong(0L);
            }
        }

        return new TransactionResponseDTO(txid, status, feeSats);
    }

    @Override
    public void checkPendingTransactions() {
        // Delegado ao BlockchainMonitorService que roda via @Scheduled
        List<PendingTransaction> pending = pendingTxRepository.findByStatus("PENDING");
        for (PendingTransaction tx : pending) {
            monitorService.checkTransaction(tx);
        }
    }

    @Override
    public TransactionResponseDTO broadcastTransaction(String rawTxHex) {
        String txid = blockchainInfo.pushSignedTransaction(rawTxHex);

        if (txid == null) {
            throw new RuntimeException("Falha ao transmitir transação");
        }

        // Registrar como pendente para monitoramento
        PendingTransaction pending = new PendingTransaction();
        pending.setTxid(txid);
        pending.setStatus("PENDING");
        pending.setRawTxHex(rawTxHex);
        // pending.setTimestamp(System.currentTimeMillis()); // CreatedAt is set in
        // constructor

        pendingTxRepository.save(pending);

        return new TransactionResponseDTO(txid, "pending", 0L);
    }

    private BigDecimal satoshisToBtc(Long satoshis) {
        return BigDecimal.valueOf(satoshis).divide(
                BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    }
}
