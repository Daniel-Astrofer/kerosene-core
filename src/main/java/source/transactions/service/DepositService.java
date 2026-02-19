package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.transactions.dto.DepositDTO;
import source.transactions.infra.BlockchainInfoClient;
import source.transactions.model.DepositEntity;
import source.transactions.repository.DepositRepository;

import source.wallet.service.WalletService;
import source.ledger.service.LedgerService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service para gerenciar depósitos de Bitcoin
 * 
 * Responsabilidades:
 * - Validar depósitos na blockchain
 * - Persistir depósitos no banco de dados
 * - Calcular saldos de depósitos confirmados
 * - Marcar depósitos como creditados
 */
@Service
public class DepositService {

    private final DepositRepository depositRepository;
    private final BlockchainInfoClient blockchainInfo;
    private final String serverDepositAddress;
    private final Long minConfirmations;

    private final WalletService walletService;
    private final LedgerService ledgerService;

    public DepositService(DepositRepository depositRepository,
            BlockchainInfoClient blockchainInfo,
            WalletService walletService,
            LedgerService ledgerService,
            @Value("${bitcoin.deposit-address:1A1z7agoat7F9gq5TF...}") String serverDepositAddress,
            @Value("${bitcoin.min-confirmations:1}") Long minConfirmations) {
        this.depositRepository = depositRepository;
        this.blockchainInfo = blockchainInfo;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.serverDepositAddress = serverDepositAddress;
        this.minConfirmations = minConfirmations;
    }

    /**
     * Retorna o endereço de depósito central do servidor
     * Todos os usuários devem enviar Bitcoin para este endereço
     */
    public String getDepositAddress() {
        return serverDepositAddress;
    }

    /**
     * Confirma um novo depósito após validação na blockchain
     * 
     * Fluxo:
     * 1. Verifica se TXID já foi registrado (previne duplicatas)
     * 2. Valida a transação na blockchain (endereço e valor corretos)
     * 3. Persiste no banco de dados com status "confirmed"
     * 4. Credita o valor na carteira do usuário (Ledger)
     * 
     * @param userId      ID do usuário que fez o depósito
     * @param txid        Hash da transação
     * @param fromAddress Endereço que enviou Bitcoin
     * @param amountBtc   Valor em BTC
     * @return DTO com dados do depósito registrado
     * @throws RuntimeException se TXID já existe ou TX não é válida
     */
    @Transactional
    public DepositDTO confirmDeposit(Long userId, String txid, String fromAddress, BigDecimal amountBtc) {
        // Validar se TX já foi registrada
        Optional<DepositEntity> existing = depositRepository.findByTxid(txid);
        if (existing.isPresent()) {
            throw new RuntimeException("Depósito já foi registrado com este TXID");
        }

        // Validar TX na blockchain
        boolean isValid = blockchainInfo.validateDepositTransaction(txid, serverDepositAddress, amountBtc);
        if (!isValid) {
            throw new RuntimeException("Transação não é válida ou não chegou ao endereço esperado");
        }

        // Criar entidade de depósito
        DepositEntity deposit = new DepositEntity();
        deposit.setUserId(userId);
        deposit.setTxid(txid);
        deposit.setFromAddress(fromAddress);
        deposit.setToAddress(serverDepositAddress);
        deposit.setAmountBtc(amountBtc);
        deposit.setStatus("confirmed"); // Marcado como confirmado após validação
        deposit.setConfirmedAt(LocalDateTime.now());
        deposit.setConfirmations(1L);

        DepositEntity saved = depositRepository.save(deposit);

        // --- CREDITA NA WALLET DO USUÁRIO ---
        try {
            // Buscar wallet do usuário
            // Assumimos que o usuário tem pelo menos uma wallet. Pegamos a
            // primeira/principal.
            // Em um sistema real, o usuário poderia escolher qual wallet depositar,
            // mas aqui simplificamos para a primeira encontrada.
            var wallets = walletService.findByUserId(userId);
            if (wallets != null && !wallets.isEmpty()) {
                var wallet = wallets.get(0);
                ledgerService.updateBalance(wallet.getId(), amountBtc, "DEPOSIT_" + txid);
                System.out.println("✅ Depósito creditado na wallet " + wallet.getId() + ": " + amountBtc + " BTC");
            } else {
                System.err.println("⚠️  Usuário " + userId + " não tem wallet para receber o depósito.");
                // Não falhamos o depósito, mas logamos o erro. O usuário pode contatar suporte.
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao creditar depósito na ledger: " + e.getMessage());
            // Dependendo da regra de negócio, poderíamos lançar exceção e rollback,
            // ou apenas registrar o depósito e tentar creditar depois (reconciliation).
            // Aqui, vamos lançar para garantir consistência (se não creditou, não confirma
            // depósito).
            throw new RuntimeException("Erro ao creditar saldo: " + e.getMessage());
        }

        return toDTO(saved);
    }

    /**
     * Consulta todos os depósitos de um usuário
     * 
     * @param userId ID do usuário
     * @return Lista com todos os depósitos (pendentes, confirmados, creditados)
     */
    public List<DepositDTO> getUserDeposits(Long userId) {
        List<DepositEntity> deposits = depositRepository.findByUserId(userId);
        return deposits.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Calcula o saldo total de depósitos creditados do usuário
     * Apenas conta depósitos com status "credited"
     * 
     * @param userId ID do usuário
     * @return Saldo em BTC (soma dos depósitos creditados)
     */
    public BigDecimal getUserDepositBalance(Long userId) {
        List<DepositEntity> deposits = depositRepository.findByUserId(userId);
        return deposits.stream()
                .filter(d -> "credited".equals(d.getStatus()))
                .map(DepositEntity::getAmountBtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Busca um depósito específico pelo TXID
     * 
     * @param txid Hash da transação do depósito
     * @return DTO com dados do depósito, ou null se não encontrado
     */
    public DepositDTO getDepositByTxid(String txid) {
        Optional<DepositEntity> deposit = depositRepository.findByTxid(txid);
        return deposit.map(this::toDTO).orElse(null);
    }

    /**
     * Marca um depósito como creditado
     * Muda o status de "confirmed" para "credited"
     * Chamado quando o saldo é sincronizado com o sistema
     * 
     * @param txid Hash da transação do depósito
     * @return DTO com depósito atualizado
     * @throws RuntimeException se depósito não encontrado
     */
    public DepositDTO creditDeposit(String txid) {
        Optional<DepositEntity> deposit = depositRepository.findByTxid(txid);
        if (deposit.isEmpty()) {
            throw new RuntimeException("Depósito não encontrado");
        }

        DepositEntity entity = deposit.get();
        entity.setStatus("credited");
        entity.setConfirmedAt(LocalDateTime.now());
        DepositEntity saved = depositRepository.save(entity);

        System.out.println("✅ Depósito creditado: TXID=" + txid + ", Usuário=" + entity.getUserId() + ", Valor="
                + entity.getAmountBtc());

        return toDTO(saved);
    }

    /**
     * Converte DepositEntity para DepositDTO
     */
    private DepositDTO toDTO(DepositEntity entity) {
        DepositDTO dto = new DepositDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setTxid(entity.getTxid());
        dto.setFromAddress(entity.getFromAddress());
        dto.setToAddress(entity.getToAddress());
        dto.setAmountBtc(entity.getAmountBtc());
        dto.setConfirmations(entity.getConfirmations());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setConfirmedAt(entity.getConfirmedAt());
        return dto;
    }
}
