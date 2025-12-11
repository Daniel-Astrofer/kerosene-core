package source.auth.application.orchestrator.login.contracts;

import source.auth.dto.contracts.UserDTOContract;
import jakarta.servlet.http.HttpServletRequest;


public interface Login {

    String loginUser(UserDTOContract dto, HttpServletRequest request);

    String loginTotpVerify(UserDTOContract dto, String deviceHash, HttpServletRequest request);

}
