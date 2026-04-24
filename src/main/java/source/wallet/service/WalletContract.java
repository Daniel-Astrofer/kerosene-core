package source.wallet.service;

import source.wallet.application.port.in.WalletAddressIndexPort;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.application.port.in.WalletManagementPort;

public interface WalletContract extends WalletLookupPort, WalletManagementPort, WalletAddressIndexPort {
}
