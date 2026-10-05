package com.kerosene.auth.application.service.device;

import com.kerosene.auth.application.infra.persistence.jpa.UserDeviceRepository;
import com.kerosene.auth.model.entity.UserDevice;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDeviceService {

    private final UserDeviceRepository deviceRepository;

    public UserDeviceService(UserDeviceRepository deviceRepository) {

        this.deviceRepository = deviceRepository;

    }

    public void create(UserDevice userDevice) {

        deviceRepository.save(userDevice);

    }

    public Optional<UserDevice> find(Long userId) {
        return deviceRepository.findByUserId(userId);
    }

    public boolean delete(UserDevice userDevice) {

        if (deviceRepository.findById(userDevice.getId()).isPresent()) {
            deviceRepository.delete(userDevice);
            return true;
        }
        return false;

    }

    public boolean update(long userId, UserDevice userDevice) {
        Optional<UserDevice> user = deviceRepository.findById(userId);

        if (user.isPresent()) {

            deviceRepository.delete(user.get());
            deviceRepository.save(userDevice);
            return true;
        }
        return false;

    }

}
