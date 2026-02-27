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
import source.transactions.dto.WithdrawRequestDTO;
import source.ledger.service.LedgerService;
import source.ledger.dto.TransactionDTO;
import source.wallet.model.WalletEntity;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final BlockchainInfoClient blockchainInfo;
    private final PendingTransactionRedisRepository pendingTxRepository;
    private final BlockchainMonitorService monitorService;
    private final source.notification.service.NotificationService notificationService;
    private final source.wallet.repository.WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final source.ledger.orchestrator.TransactionContract ledgerTransactionOrchestrator;
    private final source.ledger.repository.LedgerTransactionHistoryRepository historyRepository;

    @Value("${bitcoin.master-key:}")
    private String masterKey;

    private static final long AVERAGE_TX_SIZE_BYTES = 225; // Tamanho médio de uma TX

    public TransactionServiceImpl(BlockchainInfoClient blockchainInfo,
            PendingTransactionRedisRepository pendingTxRepository,
            BlockchainMonitorService monitorService,
            source.notification.service.NotificationService notificationService,
            source.wallet.repository.WalletRepository walletRepository,
            LedgerService ledgerService,
            source.ledger.orchestrator.TransactionContract ledgerTransactionOrchestrator,
            source.ledger.repository.LedgerTransactionHistoryRepository historyRepository) {
        this.blockchainInfo = blockchainInfo;
        this.pendingTxRepository = pendingTxRepository;
        this.monitorService = monitorService;
        this.notificationService = notificationService;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.ledgerTransactionOrchestrator = ledgerTransactionOrchestrator;
        this.historyRepository = historyRepository;
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
    public TransactionResponseDTO broadcastTransaction(String rawTxHex, String toAddress, java.math.BigDecimal amount,
            String message, Long userId) {
        String txid = blockchainInfo.pushSignedTransaction(rawTxHex);

        if (txid == null) {
            throw new RuntimeException("Falha ao transmitir transação");
        }

        // Registrar como pendente para monitoramento
        PendingTransaction pending = new PendingTransaction();
        pending.setTxid(txid);
        pending.setStatus("PENDING");
        pending.setRawTxHex(rawTxHex);
        pending.setUserId(userId);
        if (amount != null) {
            pending.setAmount(amount);
        }

        pendingTxRepository.save(pending);

        // NOTIFICAÇÃO PUSH PARA O REMETENTE (Você)
        try {
            String senderTitle = "Transação Transmitida";
            String senderBody = "A transação foi enviada para processamento na rede Blockchain.";
            if (amount != null) {
                senderBody = String.format("O envio de %s BTC foi transmitido com sucesso.",
                        amount.toPlainString());
            }
            notificationService.notifyUser(userId, senderTitle, senderBody);
        } catch (Exception e) {
            System.err.println("Erro ao notificar remetente: " + e.getMessage());
        }

        // NOTIFICAÇÃO PUSH PARA O DESTINATÁRIO
        if (toAddress != null && !toAddress.isEmpty()) {
            notifyRecipient(toAddress, txid, amount, message);
        }

        return new TransactionResponseDTO(txid, "pending", 0L);
    }

    private void notifyRecipient(String address, String txid, BigDecimal amount, String userMessage) {
        try {
            source.wallet.model.WalletEntity wallet = walletRepository.findByPassphraseHash(address);
            if (wallet != null && wallet.getUser() != null) {
                Long userId = wallet.getUser().getId();

                String title = "Recurso Recebido";
                String body = "Uma nova transferência foi identificada em sua carteira.";

                if (amount != null) {
                    body = String.format("Aporte de %s BTC identificado na carteira '%s'.",
                            amount.toPlainString(), wallet.getName());
                }

                if (userMessage != null && !userMessage.isEmpty()) {
                    body += " Mensagem: " + userMessage;
                }

                notificationService.notifyUser(userId, title, body);
            }
        } catch (Exception e) {
            // Non-blocking notification failure
            System.err.println("Failed to notify recipient: " + e.getMessage());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public TransactionResponseDTO withdraw(Long userId, WithdrawRequestDTO request) {
        System.out.println("🏦 [WITHDRAW] Processando saque para usuário " + userId);

        // 1. Validação Robusta de Dados
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor de saque deve ser positivo.");
        }

        if (!isValidBitcoinAddress(request.getToAddress())) {
            throw new RuntimeException("Endereço Bitcoin inválido: " + request.getToAddress());
        }

        // 2. Localizar Wallet e Validar Propriedade
        WalletEntity wallet = walletRepository.findByName(request.getFromWalletName());
        if (wallet == null || !wallet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Carteira de origem não encontrada ou não pertence a você.");
        }

        // 3. Estimar Taxas da Rede (para deduzir ou informar o usuário)
        EstimatedFeeDTO fees = estimateFee(request.getAmount());
        BigDecimal networkFee = fees.getEstimatedStandardBtc();
        BigDecimal totalToDebit = request.getAmount().add(networkFee);

        // 4. Verificar Saldo no Ledger Interno
        LedgerEntity ledger = ledgerService.findByWalletId(wallet.getId());
        if (ledger.getBalance().compareTo(totalToDebit) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Saldo insuficiente para cobrir o saque e as taxas de rede.");
        }

        // 5. Débito no Ledger Interno (Transação interna de "Queima" ou Transferência
        // para System Wallet)
        // Aqui realizamos uma transferência interna para a conta master do sistema para
        // conciliação
        TransactionDTO ledgerTx = new TransactionDTO();
        ledgerTx.setSender(request.getFromWalletName());
        ledgerTx.setReceiver("SYSTEM_WITHDRAWAL_VAULT"); // Entidade fictícia para tracking interno
        ledgerTx.setAmount(totalToDebit);
        ledgerTx.setContext("WITHDRAWAL_" + request.getToAddress());

        ledgerTransactionOrchestrator.processTransaction(ledgerTx);

        // 6. Criar, Assinar e Broadcast da Transação On-Chain
        // Nota: Em um sistema real, aqui carregaríamos a masterKey, construiríamos a TX
        // com bitcoinj,
        // assinaríamos e transmitiríamos. Por agora, simulamos o hex assinado.

        String dummySignedHex = "0100000001" + UUID.randomUUID().toString().replace("-", "").substring(0, 64)
                + "0000000000";

        TransactionResponseDTO response = broadcastTransaction(
                dummySignedHex,
                request.getToAddress(),
                request.getAmount(),
                "Saque On-Chain: " + request.getDescription(),
                userId);

        // Save explicit history for Withdrawal
        source.ledger.entity.LedgerTransactionHistory history = new source.ledger.entity.LedgerTransactionHistory();
        history.setId(java.util.UUID.randomUUID());
        history.setAmount(totalToDebit);
        history.setCreatedAt(java.time.LocalDateTime.now());
        history.setContext("On-Chain Withdrawal: " + request.getDescription());
        history.setSenderUserId(userId);
        history.setSenderIdentifier(request.getFromWalletName());
        history.setReceiverIdentifier(request.getToAddress());
        history.setTransactionType("WITHDRAWAL");
        history.setStatus("PENDING");
        historyRepository.save(history);

        System.out.println("✅ [WITHDRAW] Saque transmitido: " + response.getTxid());
        return response;
    }

    private boolean isValidBitcoinAddress(String address) {
        if (address == null || address.isEmpty())
            return false;
        // Basic Regex for BTC addresses (Mainnet: 1, 3, bc1)
        return address.matches("^(1|3|bc1)[a-zA-Z0-9]{25,62}$");
    }

    private BigDecimal satoshisToBtc(Long satoshis) {
        return BigDecimal.valueOf(satoshis).divide(
                BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    }
}
