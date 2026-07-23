package com.kerosene.auth.application.port.out;

import java.util.List;

import com.kerosene.auth.model.entity.PasskeyCredential;

public interface AuthPasskeyGateway {

    List<PasskeyCredential> findByUserId(Long userId);

    void deleteAll(List<PasskeyCredential> credentials);

    PasskeyCredential save(PasskeyCredential credential);
}
