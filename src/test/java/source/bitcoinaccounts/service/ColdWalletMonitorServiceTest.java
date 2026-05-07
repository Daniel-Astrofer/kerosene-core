package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import source.bitcoinaccounts.model.BitcoinAccountEntity;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletAddressEntity;
import source.bitcoinaccounts.model.ColdWalletEntity;
import source.bitcoinaccounts.model.ColdWalletUtxoEntity;
import source.bitcoinaccounts.repository.BitcoinAccountRepository;
import source.bitcoinaccounts.repository.ColdWalletAddressRepository;
import source.bitcoinaccounts.repository.ColdWalletRepository;
import source.bitcoinaccounts.repository.ColdWalletUtxoRepository;
import source.transactions.infra.BlockchainClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ColdWalletMonitorServiceTest {

    @Test
    void updatesObservedBalanceAndRecordsColdWalletTaxEventWithoutCustodialLedger() {
        ColdWalletRepository coldWalletRepository = mock(ColdWalletRepository.class);
        ColdWalletAddressRepository addressRepository = mock(ColdWalletAddressRepository.class);
        ColdWalletUtxoRepository utxoRepository = mock(ColdWalletUtxoRepository.class);
        BitcoinAccountRepository accountRepository = mock(BitcoinAccountRepository.class);
        BitcoinTaxEventService taxEventService = mock(BitcoinTaxEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        ColdWalletMonitorService service = new ColdWalletMonitorService(
                coldWalletRepository,
                addressRepository,
                utxoRepository,
                accountRepository,
                taxEventService,
                blockchainClient);

        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(42L);
        account.setType(BitcoinAccountEnums.AccountType.WATCH_ONLY_COLD_WALLET);
        account.setCustody(BitcoinAccountEnums.CustodyType.WATCH_ONLY);
        account.setLabel("Vault");

        ColdWalletEntity wallet = new ColdWalletEntity();
        wallet.setAccountId(account.getId());
        wallet.setFingerprint("d34db33f");
        wallet.setDerivationPath("m/84'/1'/0'");
        wallet.setXpub("tpub-public-only");

        ColdWalletAddressEntity address = new ColdWalletAddressEntity();
        address.setColdWalletId(wallet.getId());
        address.setAddress("bcrt1qcoldwatch000000000000000000000");
        address.setDerivationIndex(0);

        ObjectNode tx = JsonNodeFactory.instance.objectNode();
        tx.put("confirmations", 4);

        when(accountRepository.findById(wallet.getAccountId())).thenReturn(Optional.of(account));
        when(addressRepository.findByColdWalletId(wallet.getId())).thenReturn(List.of(address));
        when(blockchainClient.getUnspentOutputs(address.getAddress())).thenReturn(List.of(
                new BlockchainClient.AddressUtxo("coldtx", 1, 50_000L, "0014")));
        when(blockchainClient.getRawTransaction("coldtx", true)).thenReturn(tx);
        when(blockchainClient.executeRpc("getblockcount")).thenReturn(JsonNodeFactory.instance.numberNode(101L));
        when(utxoRepository.findByTxidAndVout("coldtx", 1)).thenReturn(Optional.empty());
        when(utxoRepository.findByColdWalletIdAndStatusIn(
                wallet.getId(),
                List.of(BitcoinAccountEnums.UtxoStatus.UNSPENT, BitcoinAccountEnums.UtxoStatus.LOCKED)))
                .thenReturn(List.of());
        when(utxoRepository.save(any(ColdWalletUtxoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(addressRepository.save(any(ColdWalletAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coldWalletRepository.save(any(ColdWalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.scanWallet(wallet);

        assertEquals(50_000L, wallet.getObservedBalanceSats());
        assertEquals(50_000L, address.getObservedBalanceSats());
        assertEquals(101L, wallet.getLastScanHeight());
        assertNotNull(address.getLastSeenAt());
        verify(taxEventService).recordTemporaryEvent(
                eq(42L),
                eq(BitcoinAccountEnums.TaxEventType.COLD_WALLET_OBSERVED_IN),
                eq(50_000L),
                eq("coldtx:1"),
                eq(account.getId()),
                eq(null),
                eq(wallet.getId()),
                eq("OBSERVED_EXTERNAL_SELF_CUSTODY"));
    }

    @Test
    void marksLockedUtxoAsSpentWhenItLeavesWatchOnlySet() {
        ColdWalletRepository coldWalletRepository = mock(ColdWalletRepository.class);
        ColdWalletAddressRepository addressRepository = mock(ColdWalletAddressRepository.class);
        ColdWalletUtxoRepository utxoRepository = mock(ColdWalletUtxoRepository.class);
        BitcoinAccountRepository accountRepository = mock(BitcoinAccountRepository.class);
        BitcoinTaxEventService taxEventService = mock(BitcoinTaxEventService.class);
        BlockchainClient blockchainClient = mock(BlockchainClient.class);
        ColdWalletMonitorService service = new ColdWalletMonitorService(
                coldWalletRepository,
                addressRepository,
                utxoRepository,
                accountRepository,
                taxEventService,
                blockchainClient);

        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(42L);
        account.setType(BitcoinAccountEnums.AccountType.WATCH_ONLY_COLD_WALLET);
        account.setCustody(BitcoinAccountEnums.CustodyType.WATCH_ONLY);

        ColdWalletEntity wallet = new ColdWalletEntity();
        wallet.setAccountId(account.getId());
        wallet.setFingerprint("d34db33f");
        wallet.setDerivationPath("m/84'/1'/0'");

        ColdWalletAddressEntity address = new ColdWalletAddressEntity();
        address.setColdWalletId(wallet.getId());
        address.setAddress("bcrt1qcoldwatch000000000000000000000");
        address.setDerivationIndex(0);

        ColdWalletUtxoEntity locked = new ColdWalletUtxoEntity();
        locked.setColdWalletId(wallet.getId());
        locked.setTxid("spenttx");
        locked.setVout(2);
        locked.setAmountSats(70_000L);
        locked.setStatus(BitcoinAccountEnums.UtxoStatus.LOCKED);

        when(accountRepository.findById(wallet.getAccountId())).thenReturn(Optional.of(account));
        when(addressRepository.findByColdWalletId(wallet.getId())).thenReturn(List.of(address));
        when(blockchainClient.getUnspentOutputs(address.getAddress())).thenReturn(List.of());
        when(blockchainClient.executeRpc("getblockcount")).thenReturn(JsonNodeFactory.instance.numberNode(102L));
        when(utxoRepository.findByColdWalletIdAndStatusIn(
                wallet.getId(),
                List.of(BitcoinAccountEnums.UtxoStatus.UNSPENT, BitcoinAccountEnums.UtxoStatus.LOCKED)))
                .thenReturn(List.of(locked));
        when(utxoRepository.save(any(ColdWalletUtxoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(addressRepository.save(any(ColdWalletAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coldWalletRepository.save(any(ColdWalletEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.scanWallet(wallet);

        assertEquals(BitcoinAccountEnums.UtxoStatus.SPENT, locked.getStatus());
        assertEquals(0L, wallet.getObservedBalanceSats());
        verify(taxEventService).recordTemporaryEvent(
                eq(42L),
                eq(BitcoinAccountEnums.TaxEventType.COLD_WALLET_OBSERVED_OUT),
                eq(70_000L),
                eq("spenttx:2"),
                eq(account.getId()),
                eq(null),
                eq(wallet.getId()),
                eq("OBSERVED_EXTERNAL_SELF_CUSTODY"));
    }
}
