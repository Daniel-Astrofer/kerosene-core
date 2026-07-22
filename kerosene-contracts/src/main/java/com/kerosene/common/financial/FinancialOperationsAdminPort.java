package source.common.financial;

import java.util.List;
import java.util.Map;

public interface FinancialOperationsAdminPort {

    Map<String, Object> blockchain();

    Map<String, Object> lightning();

    List<Map<String, Object>> logs(int limit);

    Map<String, Object> metrics();
}
