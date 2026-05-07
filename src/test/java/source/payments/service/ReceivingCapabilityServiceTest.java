package source.payments.service;

import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.payments.dto.ReceivingCapabilitiesResponse;
import source.payments.model.PaymentEnums;
import source.payments.repository.ReceivingMethodRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivingCapabilityServiceTest {

    @Test
    void reportsMissingMethodsWithoutExposingSensitiveData() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        ReceivingMethodRepository receivingMethodRepository = mock(ReceivingMethodRepository.class);
        ReceivingCapabilityService service =
                new ReceivingCapabilityService(userRepository, walletRepository, receivingMethodRepository);
        UserDataBase receiver = user(2L, "bob", true);
        when(userRepository.findByUsername("bob")).thenReturn(receiver);
        when(walletRepository.findByUserId(2L)).thenReturn(List.of(wallet(null, null)));
        when(receivingMethodRepository.findFirstByUserIdAndTypeAndStatusOrderByPriorityAsc(
                2L,
                PaymentEnums.ReceivingMethodType.LIGHTNING,
                PaymentEnums.ReceivingMethodStatus.ACTIVE)).thenReturn(Optional.empty());
        when(receivingMethodRepository.findFirstByUserIdAndTypeAndStatusOrderByPriorityAsc(
                2L,
                PaymentEnums.ReceivingMethodType.ONCHAIN,
                PaymentEnums.ReceivingMethodStatus.ACTIVE)).thenReturn(Optional.empty());

        ReceivingCapabilitiesResponse response = service.capabilities("@bob");

        assertTrue(response.canReceiveInternal());
        assertFalse(response.canReceiveLightning());
        assertFalse(response.canReceiveOnchain());
        assertEquals(PaymentEnums.PaymentRail.INTERNAL, response.preferredRail());
        assertTrue(response.missingRequirements().contains("LIGHTNING_RECEIVER_METHOD_NOT_FOUND"));
        assertTrue(response.missingRequirements().contains("ONCHAIN_METHOD_NOT_FOUND"));
        assertEquals("@bob", response.receiverDisplayName());
        assertEquals(List.of(PaymentEnums.PaymentRail.INTERNAL), response.availableRails());
        assertEquals("BTC", response.limits().asset());
        assertEquals(546L, response.limits().minOnchainSats());
        verify(walletRepository, times(1)).findByUserId(2L);
    }

    @Test
    void detectsWalletBasedLightningAndOnchainCapabilities() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        ReceivingMethodRepository receivingMethodRepository = mock(ReceivingMethodRepository.class);
        ReceivingCapabilityService service =
                new ReceivingCapabilityService(userRepository, walletRepository, receivingMethodRepository);
        UserDataBase receiver = user(2L, "bob", true);
        when(userRepository.findByUsername("bob")).thenReturn(receiver);
        when(walletRepository.findByUserId(2L)).thenReturn(List.of(wallet("bc1qexample", "bob@kerosene")));

        ReceivingCapabilitiesResponse response = service.capabilities("bob");

        assertTrue(response.canReceiveInternal());
        assertTrue(response.canReceiveLightning());
        assertTrue(response.canReceiveOnchain());
        assertEquals(List.of(
                PaymentEnums.PaymentRail.INTERNAL,
                PaymentEnums.PaymentRail.LIGHTNING,
                PaymentEnums.PaymentRail.ONCHAIN), response.availableRails());
        assertEquals("@bob", response.receiverDisplayName());
        assertTrue(response.missingRequirements().isEmpty());
        verify(walletRepository, times(1)).findByUserId(2L);
    }

    @Test
    void inactiveOrMissingReceiverReturnsPrivateNotReadyResponse() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        ReceivingMethodRepository receivingMethodRepository = mock(ReceivingMethodRepository.class);
        ReceivingCapabilityService service =
                new ReceivingCapabilityService(userRepository, walletRepository, receivingMethodRepository);
        when(userRepository.findByUsername("bob")).thenReturn(user(2L, "bob", false));

        ReceivingCapabilitiesResponse response = service.capabilities("@bob");

        assertFalse(response.canReceiveInternal());
        assertFalse(response.canReceiveLightning());
        assertFalse(response.canReceiveOnchain());
        assertEquals(List.of("RECEIVER_NOT_READY"), response.missingRequirements());
        assertEquals(List.of(), response.availableRails());
        assertEquals(null, response.receiverDisplayName());
        verify(walletRepository, times(0)).findByUserId(2L);
    }

    private UserDataBase user(Long id, String username, boolean active) {
        UserDataBase user = new UserDataBase();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setIsActive(active);
        return user;
    }

    private WalletEntity wallet(String depositAddress, String lightningAddress) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setIsActive(true);
        wallet.setDepositAddress(depositAddress);
        wallet.setLightningAddress(lightningAddress);
        return wallet;
    }
}
