package source.auth.application.orchestrator.login.contracts;

import source.auth.dto.contracts.UserDTOContract;

public interface Login {

    String loginUser(UserDTOContract dto);

    String loginTotpVerify(UserDTOContract dto);

}
