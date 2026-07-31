package com.kerosene.common.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(value = AdminLedgerService.class, ignored = NoopAdminLedgerService.class)
public class NoopAdminLedgerService implements AdminLedgerService {

    @Override
    public LedgerAccountDetail findAccount(String id) {
        throw new UnsupportedOperationException("Admin ledger is not available in standalone auth-service");
    }

    @Override
    public LedgerJournalDetail findJournal(String id) {
        throw new UnsupportedOperationException("Admin ledger is not available in standalone auth-service");
    }
}
