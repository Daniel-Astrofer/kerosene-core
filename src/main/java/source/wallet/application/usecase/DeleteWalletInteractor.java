package source.wallet.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.chain.WalletRequestHandler;
import source.wallet.application.context.DeleteWalletContext;
import source.wallet.application.handler.delete.DeleteWalletHandler;
import source.wallet.application.handler.delete.LoadWalletForDeletionHandler;
import source.wallet.application.handler.delete.VerifyWalletDeletionPassphraseHandler;
import source.wallet.application.port.in.DeleteWalletUseCase;
import source.wallet.dto.WalletRequestDTO;

@Service
@Transactional
public class DeleteWalletInteractor implements DeleteWalletUseCase {

    private final WalletRequestHandler<DeleteWalletContext> chain;

    public DeleteWalletInteractor(
            LoadWalletForDeletionHandler loadWalletForDeletionHandler,
            VerifyWalletDeletionPassphraseHandler verifyWalletDeletionPassphraseHandler,
            DeleteWalletHandler deleteWalletHandler) {
        loadWalletForDeletionHandler
                .linkWith(verifyWalletDeletionPassphraseHandler)
                .linkWith(deleteWalletHandler);

        this.chain = loadWalletForDeletionHandler;
    }

    @Override
    public void deleteWallet(WalletRequestDTO dto, Long userId) {
        chain.handle(new DeleteWalletContext(userId, dto));
    }
}
