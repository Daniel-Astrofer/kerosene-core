package source.transactions.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import source.transactions.model.PendingTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class PendingTransactionRedisRepository {

    private static final String KEY_PREFIX = "pending_tx:";
    private static final String INDEX_PENDING = "pending_tx:status:PENDING";
    private static final String INDEX_USER_PREFIX = "pending_tx:user:";
    private static final long DEFAULT_TTL_HOURS = 48; // 48 horas de TTL

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public PendingTransactionRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    /**
     * Salva uma transação pendente no Redis
     */
    public PendingTransaction save(PendingTransaction transaction) {
        try {
            String key = KEY_PREFIX + transaction.getTxid();
            PendingTransaction existing = findByTxid(transaction.getTxid());
            String json = objectMapper.writeValueAsString(transaction);

            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_HOURS, TimeUnit.HOURS);
            syncPendingIndex(transaction.getTxid(), existing != null ? existing.getStatus() : null, transaction.getStatus());
            syncUserIndex(transaction, existing);

            return transaction;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error saving pending transaction to Redis", e);
        }
    }

    /**
     * Busca transação por txid
     */
    public PendingTransaction findByTxid(String txid) {
        try {
            String key = KEY_PREFIX + txid;
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return null;
            }

            return objectMapper.readValue(json, PendingTransaction.class);
        } catch (Exception e) {
            throw new RuntimeException("Error reading pending transaction from Redis", e);
        }
    }

    /**
     * Lista todas as transações pendentes
     */
    public List<PendingTransaction> findByStatus(String status) {
        if (!"PENDING".equals(status)) {
            return new ArrayList<>(); // Por simplicidade, só indexamos PENDING
        }

        Set<String> txids = redisTemplate.opsForSet().members(INDEX_PENDING);
        if (txids == null || txids.isEmpty()) {
            return new ArrayList<>();
        }

        List<PendingTransaction> result = new ArrayList<>();
        for (String txid : txids) {
            PendingTransaction tx = findByTxid(txid);
            if (tx != null) {
                result.add(tx);
            } else {
                // Limpar índice de txid que não existe mais
                redisTemplate.opsForSet().remove(INDEX_PENDING, txid);
            }
        }

        return result;
    }

    /**
     * Lista transações de um usuário
     */
    public List<PendingTransaction> findByUserId(Long userId) {
        String userIndexKey = INDEX_USER_PREFIX + userId;
        Set<String> txids = redisTemplate.opsForSet().members(userIndexKey);

        if (txids == null || txids.isEmpty()) {
            return new ArrayList<>();
        }

        List<PendingTransaction> result = new ArrayList<>();
        for (String txid : txids) {
            PendingTransaction tx = findByTxid(txid);
            if (tx != null) {
                result.add(tx);
            } else {
                // Limpar índice
                redisTemplate.opsForSet().remove(userIndexKey, txid);
            }
        }

        return result;
    }

    /**
     * Atualiza status de uma transação
     */
    public void updateStatus(String txid, String newStatus) {
        PendingTransaction tx = findByTxid(txid);
        if (tx != null) {
            // Remover do índice PENDING se mudou de status
            if ("PENDING".equals(tx.getStatus()) && !"PENDING".equals(newStatus)) {
                redisTemplate.opsForSet().remove(INDEX_PENDING, txid);
            }

            tx.setStatus(newStatus);
            save(tx);
        }
    }

    /**
     * Remove uma transação
     */
    public void delete(String txid) {
        PendingTransaction tx = findByTxid(txid);
        if (tx != null) {
            String key = KEY_PREFIX + txid;
            redisTemplate.delete(key);

            redisTemplate.opsForSet().remove(INDEX_PENDING, txid);
            if (tx.getUserId() != null) {
                String userIndexKey = INDEX_USER_PREFIX + tx.getUserId();
                redisTemplate.opsForSet().remove(userIndexKey, txid);
            }
        }
    }

    private void syncPendingIndex(String txid, String previousStatus, String currentStatus) {
        if ("PENDING".equals(previousStatus) && !"PENDING".equals(currentStatus)) {
            redisTemplate.opsForSet().remove(INDEX_PENDING, txid);
        }

        if ("PENDING".equals(currentStatus)) {
            redisTemplate.opsForSet().add(INDEX_PENDING, txid);
        }
    }

    private void syncUserIndex(PendingTransaction transaction, PendingTransaction existing) {
        if (existing != null && existing.getUserId() != null
                && (transaction.getUserId() == null || !existing.getUserId().equals(transaction.getUserId()))) {
            redisTemplate.opsForSet().remove(INDEX_USER_PREFIX + existing.getUserId(), transaction.getTxid());
        }

        if (transaction.getUserId() != null) {
            redisTemplate.opsForSet().add(INDEX_USER_PREFIX + transaction.getUserId(), transaction.getTxid());
        }
    }
}
