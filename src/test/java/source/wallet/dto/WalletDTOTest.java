package source.wallet.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WalletDTO Tests")
class WalletDTOTest {

    @Test
    @DisplayName("Should create WalletDTO with default constructor")
    void shouldCreateWalletDTOWithDefaultConstructor() {
        WalletDTO dto = new WalletDTO();
        assertNotNull(dto);
    }

    @Test
    @DisplayName("Should set and get passphrase correctly")
    void shouldSetAndGetPassphrase() {
        WalletDTO dto = new WalletDTO();
        String passphrase = "my-secret-passphrase-123";
        
        dto.setPassphrase(passphrase);
        
        assertEquals(passphrase, dto.getPassphrase());
    }

    @Test
    @DisplayName("Should set and get name correctly")
    void shouldSetAndGetName() {
        WalletDTO dto = new WalletDTO();
        String name = "MyWallet";
        
        dto.setName(name);
        
        assertEquals(name, dto.getName());
    }

    @Test
    @DisplayName("Should set and get newName correctly")
    void shouldSetAndGetNewName() {
        WalletDTO dto = new WalletDTO();
        String newName = "MyUpdatedWallet";
        
        dto.setNewName(newName);
        
        assertEquals(newName, dto.getNewName());
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        WalletDTO dto = new WalletDTO();
        
        dto.setPassphrase(null);
        dto.setName(null);
        dto.setNewName(null);
        
        assertNull(dto.getPassphrase());
        assertNull(dto.getName());
        assertNull(dto.getNewName());
    }

    @Test
    @DisplayName("Should create complete wallet DTO for creation")
    void shouldCreateCompleteWalletDTOForCreation() {
        WalletDTO dto = new WalletDTO();
        
        dto.setPassphrase("secure-passphrase-bip39");
        dto.setName("MainWallet");
        
        assertEquals("secure-passphrase-bip39", dto.getPassphrase());
        assertEquals("MainWallet", dto.getName());
        assertNull(dto.getNewName());
    }

    @Test
    @DisplayName("Should create complete wallet DTO for update")
    void shouldCreateCompleteWalletDTOForUpdate() {
        WalletDTO dto = new WalletDTO();
        
        dto.setName("OldWalletName");
        dto.setNewName("NewWalletName");
        
        assertEquals("OldWalletName", dto.getName());
        assertEquals("NewWalletName", dto.getNewName());
    }
}
