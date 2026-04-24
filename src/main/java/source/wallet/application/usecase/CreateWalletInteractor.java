package source.wallet.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.chain.WalletRequestHandler;
import source.wallet.application.context.CreateWalletContext;
import source.wallet.application.handler.create.AllocateWalletAddressHandler;
import source.wallet.application.handler.create.CreateWalletLedgerHandler;
import source.wallet.application.handler.create.InstantiateWalletHandler;
import source.wallet.application.handler.create.LoadWalletUserHandler;
import source.wallet.application.handler.create.PersistNewWalletHandler;
import source.wallet.application.handler.create.ValidateCreateWalletRequestHandler;
import source.wallet.application.port.in.CreateWalletUseCase;
import source.wallet.application.port.out.WalletCardProfilePort;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.service.WalletResponseAssembler;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.service.WalletCardLifecycleService;

@Service
@Transactional
public class CreateWalletInteractor implements CreateWalletUseCase {

    private final WalletRequestHandler<CreateWalletContext> chain;
    private final WalletCardProfilePort walletCardProfilePort;
    private final WalletCredentialsPort walletCredentialsPort;
    private final WalletResponseAssembler walletResponseAssembler;
    private final WalletCardLifecycleService walletCardLifecycleService;

    public CreateWalletInteractor(
            LoadWalletUserHandler loadWalletUserHandler,
            ValidateCreateWalletRequestHandler validateCreateWalletRequestHandler,
            InstantiateWalletHandler instantiateWalletHandler,
            PersistNewWalletHandler persistNewWalletHandler,
            AllocateWalletAddressHandler allocateWalletAddressHandler,
            CreateWalletLedgerHandler createWalletLedgerHandler,
            WalletCardProfilePort walletCardProfilePort,
            WalletCredentialsPort walletCredentialsPort,
            WalletResponseAssembler walletResponseAssembler,
            WalletCardLifecycleService walletCardLifecycleService) {
        loadWalletUserHandler
                .linkWith(validateCreateWalletRequestHandler)
                .linkWith(instantiateWalletHandler)
                .linkWith(persistNewWalletHandler)
                .linkWith(allocateWalletAddressHandler)
                .linkWith(createWalletLedgerHandler);

        this.chain = loadWalletUserHandler;
        this.walletCardProfilePort = walletCardProfilePort;
        this.walletCredentialsPort = walletCredentialsPort;
        this.walletResponseAssembler = walletResponseAssembler;
        this.walletCardLifecycleService = walletCardLifecycleService;
    }

    @Override
    public WalletResponseDTO createWallet(WalletRequestDTO dto, Long userId) {
        CreateWalletContext context = new CreateWalletContext(userId, dto);
        chain.handle(context);

        return walletResponseAssembler.toResponse(
                context.getWallet(),
                walletCardProfilePort.resolveProfile(userId),
                walletCardLifecycleService.resolve(context.getWallet()),
                walletCredentialsPort.buildWalletTotpUri(context.getWallet().getName(), context.getTotpSecret()));
    }
}
