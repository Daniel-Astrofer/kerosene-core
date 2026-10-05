package com.kerosene.admin.service;

import java.util.List;

/**
 * Read-only contract for administrative ledger account and journal inspection.
 * Implementations may delegate to KFE or query PostgreSQL, but must return the same projection semantics.
 */
public interface AdminLedgerService {

    /**
     * Looks up the administrative projection of a ledger account.
     *
     * @param id opaque account identifier
     * @return account ownership, asset, balance, state, timestamps, and tags
     */
    LedgerAccountDetail findAccount(String id);

    /**
     * Looks up one immutable journal entry projection.
     *
     * @param id opaque journal entry identifier
     * @return account reference, movement amount/type, description, event time, and status
     */
    LedgerJournalDetail findJournal(String id);

    /**
     * Administrative view of one account and its current ledger balance.
     *
     * @param id account identifier
     * @param ownerId owning user or system identity
     * @param currency account asset/currency code
     * @param balance decimal-string balance preserving JSON numeric precision
     * @param status administrative account lifecycle status
     * @param createdAt creation timestamp in epoch milliseconds
     * @param updatedAt last update timestamp in epoch milliseconds
     * @param tags additional account classification labels
     */
    record LedgerAccountDetail(
            String id,
            String ownerId,
            String currency,
            String balance,
            String status,
            long createdAt,
            long updatedAt,
            List<String> tags) {}

    /**
     * Administrative view of one ledger journal movement.
     *
     * @param id journal entry identifier
     * @param accountId account to which the entry belongs
     * @param entryType credit, debit, or other ledger entry category
     * @param amount decimal-string amount preserving precision
     * @param currency asset/currency code for the amount
     * @param description human-readable movement description
     * @param referenceId external or originating operation reference
     * @param occurredAt occurrence timestamp in epoch milliseconds
     * @param status administrative processing status
     */
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
