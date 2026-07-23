package com.kerosene.auth.application.orchestrator.signup.port;

import java.util.List;

import com.kerosene.auth.model.entity.PasskeyCredential;

public interface PasskeyGateway {

    PasskeyCredential save(PasskeyCredential credential);

    List<PasskeyCredential> findByUserId(Long userId);
}
