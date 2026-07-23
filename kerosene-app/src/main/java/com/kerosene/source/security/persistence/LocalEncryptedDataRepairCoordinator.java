package source.security.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import source.auth.application.service.security.CosignerSecretService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local-only guard for persisted development volumes whose encrypted rows were
 * created with an older AES master key. Production must fail closed on this
 * condition; local containers can safely reset volatile app data instead of
 * spamming every scheduler with AttributeConverter errors.
 */
@Component
@ConditionalOnProperty(prefix = "kerosene.local.encrypted-data-repair", name = "enabled", havingValue = "true")
public class LocalEncryptedDataRepairCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LocalEncryptedDataRepairCoordinator.class);
    private static final String REQUIRED_CONFIRMATION = "KEROSENE_LOCAL_RESET_OK";
    private static final int MAX_FAILURE_SAMPLES = 12;

    private final DataSource dataSource;
    private final CosignerSecretService cryptoService;
    private final String confirmation;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LocalEncryptedDataRepairCoordinator(
            DataSource dataSource,
            CosignerSecretService cryptoService,
            @Value("${kerosene.local.encrypted-data-repair.confirmation:}") String confirmation) {
        this.dataSource = dataSource;
        this.cryptoService = cryptoService;
        this.confirmation = confirmation;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalStateException(
                    "[LocalEncryptedDataRepair] Refusing to run without explicit local confirmation.");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            List<String> failures = findEncryptedDataFailures(connection);
            if (failures.isEmpty()) {
                connection.commit();
                log.info("[LocalEncryptedDataRepair] Local encrypted data is compatible with the current master key.");
                return;
            }

            log.warn("[LocalEncryptedDataRepair] Detected {} encrypted rows incompatible with the current local master key. "
                    + "Resetting volatile local app state before schedulers start. samples={}",
                    failures.size(), failures);
            persistRepairEvent(connection, failures);
            resetVolatileLocalState(connection);
            connection.commit();
            log.warn("[LocalEncryptedDataRepair] Local encrypted state reset completed. "
                    + "Create a fresh local account; production deployments must migrate/re-encrypt instead of resetting.");
        } catch (Exception ex) {
            throw new IllegalStateException("[LocalEncryptedDataRepair] Failed to validate/reset local encrypted state.", ex);
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }

    private List<String> findEncryptedDataFailures(Connection connection) throws SQLException {
        List<String> failures = new ArrayList<>();
        scanStringColumn(connection, failures, "auth", "users_credentials", "id", "password_hash");
        scanStringColumn(connection, failures, "auth", "users_credentials", "id", "totp_secret");
        scanStringColumn(connection, failures, "financial", "wallets", "id", "address");
        scanStringColumn(connection, failures, "financial", "wallets", "id", "totp_secret");
        scanStringColumn(connection, failures, "financial", "wallets", "id", "xpub");
        scanBalanceColumn(connection, failures, "financial", "ledger", "id", "balance");
        scanStringColumn(connection, failures, "financial", "network_transfers", "id", "destination");
        scanStringColumn(connection, failures, "financial", "network_transfers", "id", "invoice_data");
        scanStringColumn(connection, failures, "financial", "ledger_transactions", "id", "to_address");
        scanStringColumn(connection, failures, "financial", "ledger_transactions", "id", "message");
        return failures;
    }

    private void scanStringColumn(
            Connection connection,
            List<String> failures,
            String schema,
            String table,
            String idColumn,
            String dataColumn) throws SQLException {
        if (!columnExists(connection, schema, table, dataColumn)) {
            return;
        }

        String sql = "select " + idColumn + ", " + dataColumn + " from " + schema + "." + table
                + " where " + dataColumn + " is not null";
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String dbData = resultSet.getString(2);
                if (dbData == null || dbData.isBlank()) {
                    continue;
                }
                try {
                    decryptString(dbData);
                } catch (Exception ex) {
                    addFailure(failures, schema, table, dataColumn, resultSet.getObject(1), ex);
                }
            }
        }
    }

    private void scanBalanceColumn(
            Connection connection,
            List<String> failures,
            String schema,
            String table,
            String idColumn,
            String dataColumn) throws SQLException {
        if (!columnExists(connection, schema, table, dataColumn)) {
            return;
        }

        String sql = "select " + idColumn + ", " + dataColumn + " from " + schema + "." + table
                + " where " + dataColumn + " is not null";
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String dbData = resultSet.getString(2);
                if (dbData == null || dbData.isBlank()) {
                    continue;
                }
                try {
                    decryptBalance(dbData);
                } catch (Exception ex) {
                    addFailure(failures, schema, table, dataColumn, resultSet.getObject(1), ex);
                }
            }
        }
    }

    private void addFailure(
            List<String> failures,
            String schema,
            String table,
            String column,
            Object id,
            Exception ex) {
        if (failures.size() >= MAX_FAILURE_SAMPLES) {
            return;
        }
        String reason = ex.getClass().getSimpleName();
        failures.add(schema + "." + table + "." + column + "#" + id + " (" + reason + ")");
    }

    private String decryptString(String dbData) {
        String ciphertext = extractCiphertextAndVerifyHmac(dbData);
        byte[] decrypted = cryptoService.decrypt(ciphertext);
        try {
            return new String(decrypted, StandardCharsets.UTF_8).trim();
        } finally {
            Arrays.fill(decrypted, (byte) 0);
        }
    }

    private BigDecimal decryptBalance(String dbData) {
        try {
            byte[] decrypted = cryptoService.decrypt(dbData);
            try {
                return new BigDecimal(new String(decrypted, StandardCharsets.UTF_8).trim());
            } finally {
                Arrays.fill(decrypted, (byte) 0);
            }
        } catch (Exception encryptedFailure) {
            return new BigDecimal(dbData);
        }
    }

    private String extractCiphertextAndVerifyHmac(String dbData) {
        int separator = dbData.indexOf(':');
        if (separator < 0) {
            return dbData;
        }

        String storedHmac = dbData.substring(0, separator);
        String ciphertext = dbData.substring(separator + 1);
        String expectedHmac = computeHmac(ciphertext);
        if (!MessageDigest.isEqual(
                storedHmac.getBytes(StandardCharsets.UTF_8),
                expectedHmac.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Encrypted column HMAC mismatch");
        }
        return ciphertext;
    }

    private String computeHmac(String ciphertext) {
        byte[] keyBytes = cryptoService.getMasterKeyBytes();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(ciphertext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private void persistRepairEvent(Connection connection, List<String> failures) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists public.local_encrypted_data_repair_events (
                        id bigserial primary key,
                        detected_at timestamp not null default current_timestamp,
                        reason varchar(128) not null,
                        sample_count integer not null,
                        samples text not null
                    )
                    """);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into public.local_encrypted_data_repair_events(reason, sample_count, samples)
                values (?, ?, ?)
                """)) {
            statement.setString(1, "MASTER_KEY_DATA_MISMATCH");
            statement.setInt(2, failures.size());
            statement.setString(3, Instant.now() + " " + String.join(", ", failures));
            statement.executeUpdate();
        }
    }

    private void resetVolatileLocalState(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        addIfTableExists(connection, tables, "financial", "blockchain_address_watch");
        addIfTableExists(connection, tables, "financial", "network_transfers");
        addIfTableExists(connection, tables, "financial", "ledger");
        addIfTableExists(connection, tables, "financial", "wallets");
        addIfTableExists(connection, tables, "financial", "ledger_transactions");
        addIfTableExists(connection, tables, "financial", "ledger_transaction_history");
        addIfTableExists(connection, tables, "financial", "ledger_entries");
        addIfTableExists(connection, tables, "financial", "processed_transactions");
        addIfTableExists(connection, tables, "financial", "network_transfer_events");
        addIfTableExists(connection, tables, "financial", "merkle_audit");
        addIfTableExists(connection, tables, "financial", "platform_revenue");
        addIfTableExists(connection, tables, "financial", "siphon_requests");
        addIfTableExists(connection, tables, "public", "deposits");
        addIfTableExists(connection, tables, "auth", "users_credentials");

        if (tables.isEmpty()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            //noinspection SqlSourceToSinkFlow
            statement.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
        }
    }

    private void addIfTableExists(Connection connection, List<String> tables, String schema, String table) throws SQLException {
        if (tableExists(connection, schema, table)) {
            tables.add(schema + "." + table);
        }
    }

    private boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from information_schema.tables
                where table_schema = ?
                  and table_name = ?
                limit 1
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                limit 1
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
