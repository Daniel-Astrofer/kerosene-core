package source.common.financial;

import java.util.Optional;

/**
 * Minimal user directory contract exposed to financial services.
 *
 * <p>KFE must not depend on auth persistence entities or repositories. This
 * port carries only the identity data required for financial ownership lookup.</p>
 */
public interface FinancialUserDirectoryPort {

    Optional<FinancialUserHandle> findByUsername(String username);

    Optional<FinancialUserHandle> findById(Long userId);

    record FinancialUserHandle(Long id, String username, boolean active) {
    }
}
