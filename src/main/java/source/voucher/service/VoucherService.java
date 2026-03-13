package source.voucher.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.model.entity.Voucher;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.voucher.repository.VoucherRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    // Using approx $20 worth of BTC in sats. E.g. at 90k: 22000 sats
    private static final Long INITIAL_VOUCHER_SATS = 22000L;

    private final VoucherRepository repository;
    private final UserServiceContract userService;
    private final SecureRandom random = new SecureRandom();
    private final String depositAddress;

    public VoucherService(VoucherRepository repository,
            UserServiceContract userService,
            @Value("${bitcoin.deposit-address}") String depositAddress) {
        this.repository = repository;
        this.userService = userService;
        this.depositAddress = depositAddress;
    }

    public static class VoucherRequestData {
        public final String depositAddress;
        public final Long amountSats;
        public final String pendingVoucherId; // Returning UUID to track it if they haven't paid yet

        public VoucherRequestData(String depositAddress, Long amountSats, String pendingVoucherId) {
            this.depositAddress = depositAddress;
            this.amountSats = amountSats;
            this.pendingVoucherId = pendingVoucherId;
        }
    }

    /**
     * Generates a new pending voucher request.
     * 
     * @return Data needed for the user to make an on-chain payment
     */
    @Transactional
    public VoucherRequestData requestVoucher() {
        Voucher voucher = new Voucher();
        // Since it's on-chain, we don't know the txid until they pay.
        // We temporarily store a placeholder or random uuid until they submit the txid.
        // To be safe with the unique constraint, we use "PENDING_" + UUID
        String placeholderTxid = "PENDING_" + UUID.randomUUID().toString();
        voucher.setTxid(placeholderTxid);
        voucher.setValueSats(INITIAL_VOUCHER_SATS.intValue());
        voucher.setStatus(Voucher.VoucherStatus.PENDING);

        repository.save(voucher);
        log.info("Requested new On-Chain Voucher id={}", voucher.getId());

        return new VoucherRequestData(depositAddress, INITIAL_VOUCHER_SATS, voucher.getId().toString());
    }

    /**
     * Confirms an on-chain payment.
     * 
     * @param pendingVoucherId the uuid of the pending voucher requested
     * @param txid             the transaction id provided by the user
     * @return the generated code if payment is valid, else throws exception
     */
    @Transactional
    public String confirmPayment(String pendingVoucherId, String txid) {
        // Mock support for development/testing
        if (txid != null && txid.startsWith("mock_tx_")) {
            log.warn("[VOUCHER] MOCK payment detected for pendingVoucherId: {}", pendingVoucherId);
        } else {
            // Prevent duplicate txid usage
            Optional<Voucher> existingByTxid = repository.findByTxid(txid);
            if (existingByTxid.isPresent()) {
                Voucher existing = existingByTxid.get();
                if (existing.getStatus() == Voucher.VoucherStatus.PAID) {
                    return existing.getCode();
                } else if (existing.getStatus() == Voucher.VoucherStatus.USED) {
                    throw new IllegalStateException("This transaction has already been used for an account.");
                }
            }
        }

        Voucher voucher = repository.findById(UUID.fromString(pendingVoucherId))
                .orElseThrow(
                        () -> new IllegalArgumentException("Pending voucher not found for id: " + pendingVoucherId));

        if (voucher.getStatus() != Voucher.VoucherStatus.PENDING) {
            log.info("Voucher {} is already processed. Status: {}", voucher.getId(), voucher.getStatus());
            return voucher.getCode();
        }

        // Convert sats to BTC for Blockchain Validation
        BigDecimal btcAmount = satoshisToBtc(Long.valueOf(voucher.getValueSats()));

        // TODO: Implement proper on-chain validation since BlockchainInfoClient was
        // removed
        boolean valid = true; // placeholder

        if (!valid) {
            throw new IllegalStateException("Transaction not valid, not found, or insufficient amount on-chain.");
        }

        // Generate a 12-char alphanumeric code
        String code = generateCode(12);

        voucher.setTxid(txid); // Update placeholder to the real txid
        voucher.setStatus(Voucher.VoucherStatus.PAID);
        voucher.setCode(code);
        repository.save(voucher);

        log.info("Voucher {} marked as PAID based on on-chain TX {}. Code generated.", voucher.getId(), txid);
        return code;
    }

    /**
     * Claims a voucher for account creation.
     */
    @Transactional(readOnly = false)
    public Voucher useVoucher(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Voucher code cannot be empty");
        }

        Voucher voucher = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid voucher code"));

        if (voucher.getStatus() != Voucher.VoucherStatus.PAID) {
            if (voucher.getStatus() == Voucher.VoucherStatus.USED) {
                throw new IllegalStateException("Voucher has already been used");
            } else {
                throw new IllegalStateException("Voucher payment is pending");
            }
        }

        voucher.setStatus(Voucher.VoucherStatus.USED);
        voucher.setUsedAt(LocalDateTime.now());

        return repository.save(voucher);
    }

    /**
     * Creates and immediately claims a system voucher used for the fixed BTC
     * onboarding fee.
     * This bypasses the code generation and directly links the used voucher to the
     * user
     * to set isActive = true.
     */
    @Transactional
    public Voucher createAndClaimOnboardingVoucher(Long userId, String txid, BigDecimal amountBtc) {
        // Prevent duplicate txid usage
        Optional<Voucher> existingByTxid = repository.findByTxid(txid);
        if (existingByTxid.isPresent()) {
            throw new IllegalStateException("This transaction has already been used for an onboarding fee.");
        }

        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Convert BTC to Satoshis for Voucher storage
        long amountSats = amountBtc.multiply(new BigDecimal("100000000")).longValue();

        Voucher voucher = new Voucher();
        voucher.setTxid(txid);
        voucher.setValueSats((int) amountSats);
        voucher.setStatus(Voucher.VoucherStatus.USED);
        voucher.setCode("ONBD_" + generateCode(10));
        voucher.setUsedAt(LocalDateTime.now());
        repository.save(voucher);

        // Link to user and set active
        user.setVoucher(voucher);
        user.setIsActive(true);
        userService.createUserInDataBase(user); // Actually updates if already exists

        log.info("Onboarding voucher claimed for user {} via TX {}", userId, txid);
        return voucher;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupStaleVouchers() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = repository.deletePendingOlderThan(cutoff);
        log.info("Deleted {} stale pending vouchers from database.", deleted);
    }

    private String generateCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private BigDecimal satoshisToBtc(Long satoshis) {
        if (satoshis == null)
            return BigDecimal.ZERO;
        return new BigDecimal(satoshis).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }
}
