package source.transactions.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.transactions.dto.*;
import source.transactions.service.DepositService;
import source.transactions.service.PaymentLinkService;
import source.transactions.service.TransactionService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller para operações de transações Bitcoin, depósitos e payment links
 * 
 * Endpoints disponíveis:
 * - Transações: /transactions/send, /transactions/status, /transactions/estimate-fee, /transactions/broadcast
 * - Depósitos: /transactions/deposit-address, /transactions/confirm-deposit, /transactions/deposits, etc.
 * - Payment Links: /transactions/create-payment-link, /transactions/payment-link/{linkId}, etc.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;
    private final DepositService depositService;
    private final PaymentLinkService paymentLinkService;

    public TransactionController(TransactionService service, DepositService depositService, 
                                 PaymentLinkService paymentLinkService) {
        this.service = service;
        this.depositService = depositService;
        this.paymentLinkService = paymentLinkService;
    }

    // ==================== TRANSACTION ENDPOINTS ====================

    /**
     * Estima as taxas de transação para um determinado valor
     * @param amount Valor em BTC
     * @return DTO com estimativas de taxas (Fast, Standard, Slow)
     */
    @GetMapping("/estimate-fee")
    public ResponseEntity<EstimatedFeeDTO> estimateFee(@RequestParam BigDecimal amount,
                                                       HttpServletRequest request) {
        EstimatedFeeDTO estimate = service.estimateFee(amount);
        return ResponseEntity.ok(estimate);
    }

    /**
     * Envia uma transação Bitcoin já assinada
     * @param dto DTO com dados da transação
     * @return Hash da transação (TXID) e status
     */
    @PostMapping("/send")
    public ResponseEntity<TransactionResponseDTO> sendTransaction(@RequestBody TransactionRequestDTO dto,
                                                                  HttpServletRequest request) {
        TransactionResponseDTO response = service.sendTransaction(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Consulta o status de uma transação na blockchain
     * @param txid Hash da transação
     * @return DTO com status e informações da transação
     */
    @GetMapping("/status")
    public ResponseEntity<TransactionResponseDTO> getStatus(@RequestParam String txid,
                                                            HttpServletRequest request) {
        TransactionResponseDTO response = service.getStatus(txid);
        return ResponseEntity.ok(response);
    }

    /**
     * Transmite uma transação assinada (raw hex) para a blockchain
     * @param signedTx DTO com transação raw assinada
     * @return Hash da transação (TXID)
     */
    @PostMapping("/broadcast")
    public ResponseEntity<TransactionResponseDTO> broadcastSignedTx(@RequestBody SignedTransactionDTO signedTx,
                                                                    HttpServletRequest request) {
        TransactionResponseDTO response = service.broadcastSignedTransaction(signedTx);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ==================== DEPOSIT ENDPOINTS ====================

    /**
     * Retorna o endereço de depósito central do servidor
     * Todos os depósitos devem ser enviados para este endereço
     * @return Endereço Bitcoin do servidor
     */
    @GetMapping("/deposit-address")
    public ResponseEntity<String> getDepositAddress(HttpServletRequest request) {
        String address = depositService.getDepositAddress();
        return ResponseEntity.ok(address);
    }

    /**
     * Confirma um novo depósito após validação na blockchain
     * Requer autenticação do usuário
     * @param req DTO com TXID, endereço de origem e valor
     * @return DTO com detalhes do depósito registrado
     */
    @PostMapping("/confirm-deposit")
    public ResponseEntity<DepositDTO> confirmDeposit(@RequestBody DepositConfirmRequest req,
                                                      HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        DepositDTO deposit = depositService.confirmDeposit(
                userId,
                req.getTxid(),
                req.getFromAddress(),
                req.getAmount()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(deposit);
    }

    /**
     * Lista todos os depósitos do usuário autenticado
     * @return Lista de depósitos (pending, confirmed, credited)
     */
    @GetMapping("/deposits")
    public ResponseEntity<List<DepositDTO>> getUserDeposits(HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        List<DepositDTO> deposits = depositService.getUserDeposits(userId);
        return ResponseEntity.ok(deposits);
    }

    /**
     * Consulta o saldo total de depósitos creditados do usuário
     * @return Saldo em BTC (apenas depósitos com status "credited")
     */
    @GetMapping("/deposit-balance")
    public ResponseEntity<BigDecimal> getDepositBalance(HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        BigDecimal balance = depositService.getUserDepositBalance(userId);
        return ResponseEntity.ok(balance);
    }

    /**
     * Obtém detalhes de um depósito específico pelo TXID
     * @param txid Hash da transação do depósito
     * @return DTO com dados do depósito
     */
    @GetMapping("/deposit/{txid}")
    public ResponseEntity<DepositDTO> getDeposit(@PathVariable String txid,
                                                  HttpServletRequest request) {
        DepositDTO deposit = depositService.getDepositByTxid(txid);
        if (deposit == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(deposit);
    }

    // ==================== PAYMENT LINK ENDPOINTS ====================

    /**
     * Cria um novo payment link para receber pagamentos
     * O link expira após o tempo configurado (padrão: 60 minutos)
     * Requer autenticação do usuário
     * @param req DTO com valor (BTC) e descrição
     * @return DTO com ID do payment link e demais informações
     */
    @PostMapping("/create-payment-link")
    public ResponseEntity<PaymentLinkDTO> createPaymentLink(@RequestBody CreatePaymentLinkRequest req,
                                                            HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, req.getAmount(), req.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(link);
    }

    /**
     * Obtém informações de um payment link
     * @param linkId ID único do payment link
     * @return DTO com dados do payment link (público, sem autenticação)
     */
    @GetMapping("/payment-link/{linkId}")
    public ResponseEntity<PaymentLinkDTO> getPaymentLink(@PathVariable String linkId,
                                                         HttpServletRequest request) {
        PaymentLinkDTO link = paymentLinkService.getPaymentLink(linkId);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(link);
    }

    /**
     * Confirma o pagamento de um payment link
     * Valida a transação na blockchain antes de confirmar
     * @param linkId ID do payment link
     * @param req DTO com TXID e endereço de origem
     * @return DTO com payment link atualizado (status: "paid")
     */
    @PostMapping("/payment-link/{linkId}/confirm")
    public ResponseEntity<PaymentLinkDTO> confirmPayment(@PathVariable String linkId,
                                                         @RequestBody ConfirmPaymentRequest req,
                                                         HttpServletRequest request) {
        PaymentLinkDTO link = paymentLinkService.confirmPayment(linkId, req.getTxid(), req.getFromAddress());
        return ResponseEntity.ok(link);
    }

    /**
     * Completa/libera um payment link já pago
     * Só funciona se o payment link está com status "paid"
     * Requer autenticação e que o usuário seja o dono do link
     * @param linkId ID do payment link
     * @return DTO com payment link atualizado (status: "completed")
     */
    @PostMapping("/payment-link/{linkId}/complete")
    public ResponseEntity<PaymentLinkDTO> completePayment(@PathVariable String linkId,
                                                          HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        PaymentLinkDTO link = paymentLinkService.getPaymentLink(linkId);
        if (link == null || !link.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        PaymentLinkDTO completed = paymentLinkService.completePayment(linkId);
        return ResponseEntity.ok(completed);
    }

    /**
     * Lista todos os payment links do usuário autenticado
     * @return Lista de payment links (pending, paid, expired, completed)
     */
    @GetMapping("/payment-links")
    public ResponseEntity<List<PaymentLinkDTO>> getUserPaymentLinks(HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        List<PaymentLinkDTO> links = paymentLinkService.getUserPaymentLinks(userId);
        return ResponseEntity.ok(links);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Extrai o ID do usuário autenticado do JWT token
     * @return ID do usuário extraído do principal do SecurityContext
     */
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}



