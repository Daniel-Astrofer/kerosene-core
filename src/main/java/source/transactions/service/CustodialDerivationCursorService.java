package source.transactions.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.model.CustodialDerivationCursorEntity;
import source.transactions.repository.CustodialDerivationCursorRepository;

@Service
public class CustodialDerivationCursorService {

    public static final String KEROSENE_BIP84_EXTERNAL = "KEROSENE_BIP84_EXTERNAL";

    private final CustodialDerivationCursorRepository repository;

    public CustodialDerivationCursorService(CustodialDerivationCursorRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int nextIndex(String cursorKey) {
        CustodialDerivationCursorEntity cursor = repository.findByCursorKeyForUpdate(cursorKey)
                .orElseGet(() -> newCursor(cursorKey));

        int current = cursor.getLastIssuedIndex() != null ? cursor.getLastIssuedIndex() : -1;
        int next = current + 1;
        cursor.setLastIssuedIndex(next);
        repository.save(cursor);
        return next;
    }

    private CustodialDerivationCursorEntity newCursor(String cursorKey) {
        CustodialDerivationCursorEntity cursor = new CustodialDerivationCursorEntity();
        cursor.setCursorKey(cursorKey);
        cursor.setLastIssuedIndex(-1);
        return cursor;
    }
}
