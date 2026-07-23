package com.kerosene.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.kerosene.common.financial.FinancialColdWalletPsbtApprovalRequest;
import com.kerosene.common.financial.FinancialCustodyTransferApprovalRequest;
import com.kerosene.common.financial.FinancialLocalFactorApprovalRequest;
import com.kerosene.common.financial.FinancialTransactionApprovalPort;
import com.kerosene.common.financial.FinancialWalletOutboundApprovalRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KfeInternalTransactionApprovalControllerTest {

    private final FinancialTransactionApprovalPort approvalPort = mock(FinancialTransactionApprovalPort.class);
    private final KfeInternalTransactionApprovalController controller =
            new KfeInternalTransactionApprovalController(approvalPort, "credential");

    @Test
    void approvesLocalFactorWhenCredentialMatches() {
        controller.approveLocalFactor(
                "credential",
                new FinancialLocalFactorApprovalRequest(42L, "device", "1234"));

        verify(approvalPort).approveLocalFactor(42L, "device", "1234");
    }

    @Test
    void approvesCustodyTransferWhenCredentialMatches() {
        controller.approveCustodyTransfer(
                "credential",
                new FinancialCustodyTransferApprovalRequest(42L, "assertion"));

        verify(approvalPort).approveCustodyTransfer(42L, "assertion");
    }

    @Test
    void approvesWalletOutboundWhenCredentialMatches() {
        controller.approveWalletOutbound(
                "credential",
                new FinancialWalletOutboundApprovalRequest(41L, 42L, "totp", "assertion", "phrase"));

        verify(approvalPort).approveWalletOutbound(41L, 42L, "totp", "assertion", "phrase");
    }

    @Test
    void approvesColdWalletPsbtWhenCredentialMatches() {
        controller.approveColdWalletPsbt(
                "credential",
                new FinancialColdWalletPsbtApprovalRequest(42L, "totp"));

        verify(approvalPort).approveColdWalletPsbt(42L, "totp");
    }

    @Test
    void rejectsInvalidCredential() {
        assertThrows(
                ResponseStatusException.class,
                () -> controller.approveLocalFactor(
                        "wrong",
                        new FinancialLocalFactorApprovalRequest(42L, "device", "1234")));
    }

    @Test
    void rejectsMissingUserId() {
        assertThrows(
                ResponseStatusException.class,
                () -> controller.approveLocalFactor(
                        "credential",
                        new FinancialLocalFactorApprovalRequest(null, "device", "1234")));
    }
}
