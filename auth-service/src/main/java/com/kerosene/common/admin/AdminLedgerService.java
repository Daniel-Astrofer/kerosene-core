package com.kerosene.common.admin;

import java.util.List;

/**
 * Service interface for ledger admin operations.
 * Implementations may delegate to the KFE service or PostgreSQL directly.
 */
public interface AdminLedgerService {

    LedgerAccountDetail findAccount(String id);

    LedgerJournalDetail findJournal(String id);

    record LedgerAccountDetail(
            String id,
            String ownerId,
            String currency,
            String balance,
            String status,
            long createdAt,
            long updatedAt,
            List<String> tags) {}

    record LedgerJournalDetail(
            String id,
            String accountId,
            String entryType,
            String amount,
            String currency,
            String description,
            String referenceId,
            long occurredAt,
            String status) {}
}
