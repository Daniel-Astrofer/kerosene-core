package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainInfoClient;

import source.wallet.service.WalletService;
import source.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service para gerenciar payment links (links de pagamento com expiração)
 * 
 * IMPORTANTE: Payment links são armazenados APENAS em Redis (não em banco de
 * dados)
 * 
 * Responsabilidades:
 * - Criar payment links com expiração
 * - Validar pagamentos via blockchain
 * - Gerenciar status (pending, paid, expired, completed)
 * - Limpar dados expirados automaticamente (TTL do Redis)
 */
@Service
public class PaymentLinkService {

    private final RedisTemplate<String, PaymentLinkDTO> redisTemplate;
    private final BlockchainInfoClient blockchainInfo;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final String serverDepositAddress;
    private final Long paymentLinkExpirationMinutes;

    // Constantes para chaves Redis
    private static final String REDIS_KEY_PREFIX = "payment_link:";
    private static final String REDIS_USER_INDEX_PREFIX = "user_payment_links:";
    private static final Long REDIS_TTL_HOURS = 3L; // Dados persistem por 3 horas no Redis

    public PaymentLinkService(RedisTemplate<String, PaymentLinkDTO> redisTemplate,
            BlockchainInfoClient blockchainInfo,
            WalletService walletService,
            LedgerService ledgerService,
            @Value("${bitcoin.deposit-address:1A1z7agoat7F9gq5TF...}") String serverDepositAddress,
            @Value("${bitcoin.payment-link-expiration-minutes:60}") Long paymentLinkExpirationMinutes) {
        this.redisTemplate = redisTemplate;
        this.blockchainInfo = blockchainInfo;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.serverDepositAddress = serverDepositAddress;
        this.paymentLinkExpirationMinutes = paymentLinkExpirationMinutes;
    }

    /**
     * Cria um novo payment link e armazena no Redis
     * 
     * Fluxo:
     * 1. Gera ID único (pay_<UUID>)
     * 2. Define expiração (padrão 60 minutos)
     * 3. Armazena no Redis com TTL de 3 horas
     * 4. Adiciona à lista de links do usuário
     * 
     * @param userId      ID do usuário criador
     * @param amountBtc   Valor em BTC
     * @param description Descrição do pagamento
     * @return DTO com dados do link criado
     */
    public PaymentLinkDTO createPaymentLink(Long userId, BigDecimal amountBtc, String description) {
        String linkId = generatePaymentLinkId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(paymentLinkExpirationMinutes);

        PaymentLinkDTO dto = new PaymentLinkDTO();
        dto.setId(linkId);
        dto.setUserId(userId);
        dto.setAmountBtc(amountBtc);
        dto.setDescription(description);
        dto.setDepositAddress(serverDepositAddress);
        dto.setStatus("pending");
        dto.setExpiresAt(expiresAt);
        dto.setCreatedAt(now);

        // Armazenar no Redis (única fonte de dados para payment links)
        String redisKey = REDIS_KEY_PREFIX + linkId;
        redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);

        // Adicionar ao índice do usuário
        // Chave formato: user_payment_links:{userId}:{linkId}
        String userLinkKey = REDIS_USER_INDEX_PREFIX + userId + ":" + linkId;
        redisTemplate.opsForValue().set(userLinkKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);

        System.out.println("✅ Payment Link criado no Redis: " + linkId);
        return dto;
    }

    /**
     * Consulta um payment link no Redis
     * Verifica automaticamente se expirou
     * 
     * @param linkId ID do payment link
     * @return DTO com dados do link, ou null se não encontrado
     */
    public PaymentLinkDTO getPaymentLink(String linkId) {
        String redisKey = REDIS_KEY_PREFIX + linkId;
        PaymentLinkDTO dto = redisTemplate.opsForValue().get(redisKey);

        if (dto == null) {
            return null;
        }

        System.out.println("✅ Payment Link recuperado do Redis: " + linkId);

        // Verificar expiração automática
        if (LocalDateTime.now().isAfter(dto.getExpiresAt()) && "pending".equals(dto.getStatus())) {
            dto.setStatus("expired");
            redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);
            System.out.println("⚠️  Payment Link expirou: " + linkId);
        }

        return dto;
    }

    /**
     * Confirma o pagamento de um payment link
     * 
     * Validações:
     * 1. Link deve estar com status "pending"
     * 2. Link não pode estar expirado
     * 3. Transação deve ser válida na blockchain
     * 4. Valor da TX deve corresponder ao esperado
     * 
     * Após validação, muda status para "paid"
     * 
     * @param linkId      ID do payment link
     * @param txid        Hash da transação de pagamento
     * @param fromAddress Endereço que enviou Bitcoin
     * @return DTO com link atualizado
     * @throws RuntimeException se validações falharem
     */
    public PaymentLinkDTO confirmPayment(String linkId, String txid, String fromAddress) {
        String redisKey = REDIS_KEY_PREFIX + linkId;
        PaymentLinkDTO dto = redisTemplate.opsForValue().get(redisKey);

        if (dto == null) {
            throw new RuntimeException("Payment link não encontrado");
        }

        // Validar status
        if (!"pending".equals(dto.getStatus())) {
            throw new RuntimeException("Payment link já foi processado ou expirou");
        }

        // Validar expiração
        if (LocalDateTime.now().isAfter(dto.getExpiresAt())) {
            dto.setStatus("expired");
            redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);
            throw new RuntimeException("Payment link expirou");
        }

        // Validar TX na blockchain
        boolean isValid = blockchainInfo.validateDepositTransaction(
                txid,
                serverDepositAddress,
                dto.getAmountBtc());

        if (!isValid) {
            throw new RuntimeException("Transação não é válida");
        }

        // Marcar como pago
        dto.setStatus("paid");
        dto.setTxid(txid);
        dto.setPaidAt(LocalDateTime.now());

        // Atualizar no Redis
        redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);

        System.out
                .println("✅ Pagamento confirmado: Link=" + linkId + ", TXID=" + txid + ", Valor=" + dto.getAmountBtc());

        // --- CREDITAR NO LEDGER DO USUÁRIO ---
        try {
            Long userId = dto.getUserId();
            var wallets = walletService.findByUserId(userId);
            if (wallets != null && !wallets.isEmpty()) {
                var wallet = wallets.get(0);
                ledgerService.updateBalance(wallet.getId(), dto.getAmountBtc(), "PAYMENT_LINK_" + linkId);
                System.out
                        .println("✅ Valor creditado na wallet " + wallet.getId() + ": " + dto.getAmountBtc() + " BTC");
            } else {
                System.err.println("⚠️  Usuário " + userId + " não tem wallet para receber o pagamento.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao creditar pagamento na ledger: " + e.getMessage());
            // Lançar exceção para garantir consistência?
            // Se falhar o crédito, o link não deveria ficar "paid".
            // Vamos reverter status para pending se falhar (manual rollback na memória)
            dto.setStatus("pending");
            dto.setTxid(null);
            dto.setPaidAt(null);
            redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);
            throw new RuntimeException("Erro ao creditar saldo: " + e.getMessage());
        }

        return dto;
    }

    /**
     * Completa/libera um payment link
     * Muda status de "paid" para "completed"
     * Só funciona se o link está com status "paid"
     * 
     * @param linkId ID do payment link
     * @return DTO com link atualizado
     * @throws RuntimeException se link não está "paid"
     */
    public PaymentLinkDTO completePayment(String linkId) {
        String redisKey = REDIS_KEY_PREFIX + linkId;
        PaymentLinkDTO dto = redisTemplate.opsForValue().get(redisKey);

        if (dto == null) {
            throw new RuntimeException("Payment link não encontrado");
        }

        if (!"paid".equals(dto.getStatus())) {
            throw new RuntimeException("Payment link precisa estar 'paid' para ser completado");
        }

        dto.setStatus("completed");
        dto.setCompletedAt(LocalDateTime.now());

        // Atualizar no Redis
        redisTemplate.opsForValue().set(redisKey, dto, REDIS_TTL_HOURS, TimeUnit.HOURS);

        System.out.println("✅ Pagamento liberado: Link=" + linkId + ", Valor=" + dto.getAmountBtc());
        return dto;
    }

    /**
     * Lista todos os payment links de um usuário
     * 
     * Nota: Esta implementação busca todos os links no Redis
     * Se houver muitos links, considerar implementar índice dedicado
     * 
     * @param userId ID do usuário
     * @return Lista com todos os links do usuário (pending, paid, expired,
     *         completed)
     */
    public List<PaymentLinkDTO> getUserPaymentLinks(Long userId) {
        // Busca baseada no padrão de chave definido em createPaymentLink:
        // user_payment_links:{userId}:{linkId}
        String pattern = REDIS_USER_INDEX_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }

        List<PaymentLinkDTO> links = new ArrayList<>();
        for (String key : keys) {
            // A chave contém o DTO (cópia) ou podemos pegar o ID e buscar o original?
            // Em createPaymentLink salvamos o DTO na chave de índice também.
            PaymentLinkDTO dto = redisTemplate.opsForValue().get(key);
            if (dto != null) {
                // Verificar se o status atualizou na chave principal (payment_link:{id})
                // A chave de índice pode estar desatualizada se atualizamos apenas a chave
                // principal.
                // Então melhor pegar o ID da chave e buscar na principal.
                // Chave formato: user_payment_links:{userId}:{linkId}
                String[] parts = key.split(":");
                if (parts.length > 0) {
                    String linkId = parts[parts.length - 1];
                    PaymentLinkDTO freshDto = getPaymentLink(linkId);
                    if (freshDto != null) {
                        links.add(freshDto);
                    } else {
                        // Se não existe mais (expirou e sumiu), remover do índice
                        redisTemplate.delete(key);
                    }
                }
            }
        }

        // Ordenar por data de criação decrescente
        links.sort(Comparator.comparing(PaymentLinkDTO::getCreatedAt).reversed());

        return links;
    }

    /**
     * Remove um payment link do Redis
     * 
     * @param linkId ID do payment link
     */
    public void removeFromRedis(String linkId) {
        String redisKey = REDIS_KEY_PREFIX + linkId;
        Boolean deleted = redisTemplate.delete(redisKey);

        if (deleted != null && deleted) {
            System.out.println("✅ Payment Link removido do Redis: " + linkId);
        }
    }

    /**
     * Gera um ID único para o payment link
     * Formato: pay_<12 caracteres aleatórios>
     */
    private String generatePaymentLinkId() {
        return "pay_" + UUID.randomUUID().toString().substring(0, 12);
    }
}
