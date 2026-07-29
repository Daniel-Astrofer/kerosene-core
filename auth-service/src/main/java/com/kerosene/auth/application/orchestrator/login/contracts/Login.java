package com.kerosene.auth.application.orchestrator.login.contracts;

import com.kerosene.auth.dto.contracts.UserDTOContract;

public interface Login {

    String loginUser(UserDTOContract dto);

    String loginTotpVerify(UserDTOContract dto);

}
