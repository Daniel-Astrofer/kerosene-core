package source.wallet.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.chain.WalletRequestHandler;
import source.wallet.application.context.UpdateWalletContext;
import source.wallet.application.handler.update.ApplyWalletNameUpdateHandler;
import source.wallet.application.handler.update.ApplyWalletXpubUpdateHandler;
import source.wallet.application.handler.update.EnsureWalletNameAvailabilityHandler;
import source.wallet.application.handler.update.LoadWalletForUpdateHandler;
import source.wallet.application.handler.update.PersistWalletUpdateHandler;
import source.wallet.application.handler.update.ValidateUpdateWalletRequestHandler;
import source.wallet.application.handler.update.VerifyWalletUpdatePassphraseHandler;
import source.wallet.application.port.in.UpdateWalletUseCase;
import source.wallet.dto.WalletUpdateDTO;

@Service
@Transactional
public class UpdateWalletInteractor implements UpdateWalletUseCase {

    private final WalletRequestHandler<UpdateWalletContext> chain;

    public UpdateWalletInteractor(
            LoadWalletForUpdateHandler loadWalletForUpdateHandler,
            ValidateUpdateWalletRequestHandler validateUpdateWalletRequestHandler,
            VerifyWalletUpdatePassphraseHandler verifyWalletUpdatePassphraseHandler,
            EnsureWalletNameAvailabilityHandler ensureWalletNameAvailabilityHandler,
            ApplyWalletNameUpdateHandler applyWalletNameUpdateHandler,
            ApplyWalletXpubUpdateHandler applyWalletXpubUpdateHandler,
            PersistWalletUpdateHandler persistWalletUpdateHandler) {
        loadWalletForUpdateHandler
                .linkWith(validateUpdateWalletRequestHandler)
                .linkWith(verifyWalletUpdatePassphraseHandler)
                .linkWith(ensureWalletNameAvailabilityHandler)
                .linkWith(applyWalletNameUpdateHandler)
                .linkWith(applyWalletXpubUpdateHandler)
                .linkWith(persistWalletUpdateHandler);

        this.chain = loadWalletForUpdateHandler;
    }

    @Override
    public void updateWallet(WalletUpdateDTO dto, Long userId) {
        chain.handle(new UpdateWalletContext(userId, dto));
    }
}
