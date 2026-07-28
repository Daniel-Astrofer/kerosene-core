package com.kerosene.common.vaultmesh;

import java.util.Optional;

public interface VaultDepositDescriptorPort {
    Optional<VaultMeshDepositInfo> getUsersDepositAddress();
    Optional<VaultMeshDepositInfo> getChannelsDepositAddress();
}
