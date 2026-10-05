package com.kerosene.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.auth.application.usecase.activation.AccountActivationOperationsUseCase;
import com.kerosene.auth.dto.AccountActivationStatusDTO;
import com.kerosene.common.dto.ApiResponse;

import java.util.Map;

/**
 * Controller de ativação de conta e funding inicial de novos usuários.
 *
 * <p>Gerencia a verificação de status de ativação, geração de links de depósito
 * e confirmação manual/reativa de depósitos para liberação de uso da plataforma.</p>
 */
@RestController
@RequestMapping("/auth/activation-status")
public class AccountActivationController {

    private final AccountActivationOperationsUseCase accountActivationOperationsUseCase;

    public AccountActivationController(AccountActivationOperationsUseCase accountActivationOperationsUseCase) {
        this.accountActivationOperationsUseCase = accountActivationOperationsUseCase;
    }

    /**
     * Consulta o status atual de ativação da conta do usuário autenticado.
     *
     * <p><b>Endpoint:</b> {@code GET /auth/activation-status}<br>
     * <b>Autenticação:</b> Obrigatória (JWT de usuário)<br>
     * <b>Respostas:</b></p>
     * <ul>
     *   <li>{@code 200 OK} - Status retornado com sucesso contendo {@link AccountActivationStatusDTO}.</li>
     *   <li>{@code 401 UNAUTHORIZED} - Token ausente ou sessão expirada.</li>
     * </ul>
     *
     * @param authentication objeto de autenticação injetado pelo Spring Security
     * @return envelope {@link ApiResponse} contendo o DTO de status
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> getStatus(Authentication authentication) {
        AccountActivationStatusDTO status = accountActivationOperationsUseCase.getStatus(authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Activation status retrieved successfully.", status));
    }

    /**
     * Cria ou reutiliza um link de funding (on-chain ou Lightning) para ativação da conta.
     *
     * <p><b>Endpoint:</b> {@code POST /auth/activation-status/funding-link}<br>
     * <b>Autenticação:</b> Obrigatória (JWT de usuário)<br>
     * <b>Respostas:</b></p>
     * <ul>
     *   <li>{@code 200 OK} - Link preparado contendo endereço/invoice e valor mínimo em satoshis.</li>
     *   <li>{@code 401 UNAUTHORIZED} - Usuário não autenticado.</li>
     * </ul>
     *
     * @param authentication credenciais do usuário autenticado
     * @return envelope com status de ativação e dados do link gerado
     */
    @PostMapping("/funding-link")
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> createFundingLink(Authentication authentication) {
        AccountActivationStatusDTO status =
                accountActivationOperationsUseCase.createOrReuseLink(authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                "Initial funding is prepared inside the KFE flow.",
                status));
    }

    /**
     * Confirma um depósito recebido para o link de ativação informado.
     *
     * <p><b>Endpoint:</b> {@code POST /auth/activation-status/{linkId}/confirm}<br>
     * <b>Autenticação:</b> Obrigatória (JWT de usuário)<br>
     * <b>Respostas:</b></p>
     * <ul>
     *   <li>{@code 200 OK} - Depósito confirmado e status atualizado.</li>
     *   <li>{@code 400 BAD REQUEST} - Identificador de link inválido ou divergência de dados.</li>
     *   <li>{@code 401 UNAUTHORIZED} - Usuário não autenticado.</li>
     * </ul>
     *
     * @param linkId identificador do link de funding gerado previamente
     * @param request corpo contendo os campos {@code txid} e opcionalmente {@code fromAddress}
     * @param authentication credenciais do usuário autenticado
     * @return envelope com status de ativação atualizado
     */
    @PostMapping("/{linkId}/confirm")
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> confirm(
            @PathVariable String linkId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        AccountActivationStatusDTO status = accountActivationOperationsUseCase.confirm(
                authenticatedUserId(authentication),
                linkId,
                request.get("txid"),
                request.get("fromAddress"));
        return ResponseEntity.ok(ApiResponse.success("Activation status retrieved successfully.", status));
    }

    private Long authenticatedUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
