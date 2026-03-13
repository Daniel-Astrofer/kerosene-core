package source.transactions.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import source.transactions.dto.*;
import source.transactions.service.PaymentLinkService;
import source.transactions.service.TransactionService;
import source.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller para operações de transações Bitcoin, depósitos e payment links
 * 
 * Endpoints disponíveis:
 * - Transações: /transactions/send, /transactions/status,
 * /transactions/estimate-fee, /transactions/broadcast
 * - Depósitos: /transactions/deposit-address, /transactions/confirm-deposit,
 * /transactions/deposits, etc.
 * - Payment Links: /transactions/create-payment-link,
 * /transactions/payment-link/{linkId}, etc.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

        private final TransactionService service;
        private final PaymentLinkService paymentLinkService;

        @Value("${bitcoin.deposit-address}")
        private String systemDepositAddress;

        public TransactionController(TransactionService service, PaymentLinkService paymentLinkService) {
                this.service = service;
                this.paymentLinkService = paymentLinkService;
        }

        // ==================== DEPOSIT ENDPOINTS ====================

        /**
         * Obtém o endereço de depósito mestre do sistema para o usuário financiar sua
         * conta
         * 
         * @return Endereço Bitcoin (Base58 ou Bech32)
         */
        @GetMapping("/deposit-address")
        public ResponseEntity<ApiResponse<String>> getDepositAddress(HttpServletRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                "System master deposit address retrieved successfully. Please send only Bitcoin (BTC) to this address.",
                                systemDepositAddress));
        }

        // ==================== TRANSACTION ENDPOINTS ====================

        /**
         * Estima as taxas de transação para um determinado valor
         * 
         * @param amount Valor em BTC
         * @return DTO com estimativas de taxas (Fast, Standard, Slow)
         */
        @GetMapping("/estimate-fee")
        public ResponseEntity<ApiResponse<EstimatedFeeDTO>> estimateFee(@RequestParam BigDecimal amount,
                        HttpServletRequest request) {
                EstimatedFeeDTO estimate = service.estimateFee(amount);
                return ResponseEntity.ok(ApiResponse.success(
                                "Fee estimation calculated successfully based on current blockchain network conditions.",
                                estimate));
        }

        /**
         * Cria uma transação não assinada para o cliente assinar na carteira
         * O servidor retorna a raw transaction e registra para monitoramento
         * O cliente deve assinar e fazer broadcast na sua carteira
         * 
         * @param dto DTO com dados da transação (from, to, amount, fee)
         * @return DTO com transação não assinada (raw hex) e txid temporário
         */
        @PostMapping("/create-unsigned")
        public ResponseEntity<ApiResponse<UnsignedTransactionDTO>> createUnsignedTransaction(
                        @RequestBody TransactionRequestDTO dto,
                        HttpServletRequest request) {
                UnsignedTransactionDTO unsignedTx = service.createUnsignedTransaction(dto);
                return ResponseEntity.ok(ApiResponse.success(
                                "Unsigned transaction successfully generated. Please use your secure wallet to sign it.",
                                unsignedTx));
        }

        /**
         * Consulta o status de uma transação na blockchain
         * O servidor monitora automaticamente transações pendentes
         * 
         * @param txid Hash da transação
         * @return DTO com status, confirmações e informações da transação
         */
        @GetMapping("/status")
        public ResponseEntity<ApiResponse<TransactionResponseDTO>> getStatus(@RequestParam String txid,
                        HttpServletRequest request) {
                TransactionResponseDTO response = service.getTransactionStatus(txid);
                return ResponseEntity
                                .ok(ApiResponse.success(
                                                "Transaction status retrieved successfully from the blockchain.",
                                                response));
        }

        /**
         * Transmite uma raw transaction (hex) assinada para a rede Bitcoin
         * 
         * @param dto DTO com o hex da transação
         * @return DTO com o TXID gerado e status inicial
         */
        @PostMapping("/broadcast")
        public ResponseEntity<ApiResponse<TransactionResponseDTO>> broadcastTransaction(
                        @RequestBody BroadcastTransactionDTO dto,
                        Authentication auth) {
                Long userId = getAuthenticatedUserId(auth);
                TransactionResponseDTO response = service.broadcastTransaction(dto.getRawTxHex(), dto.getToAddress(),
                                dto.getAmount(), dto.getMessage(), userId);
                return ResponseEntity.ok(ApiResponse
                                .success("Your transaction has been securely broadcasted to the Bitcoin network.",
                                                response));
        }

        // ==================== PAYMENT LINK ENDPOINTS ====================

        /**
         * Cria um novo payment link para receber pagamentos
         * O link expira após o tempo configurado (padrão: 60 minutos)
         * Requer autenticação do usuário
         * 
         * @param req DTO com valor (BTC) e descrição
         * @return DTO com ID do payment link e demais informações
         */
        @PostMapping("/create-payment-link")
        public ResponseEntity<ApiResponse<PaymentLinkDTO>> createPaymentLink(@RequestBody CreatePaymentLinkRequest req,
                        Authentication auth) {
                Long userId = getAuthenticatedUserId(auth);
                PaymentLinkDTO link = paymentLinkService.createPaymentLink(userId, req.getAmount(),
                                req.getDescription());
                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse
                                .success("Your new payment link has been successfully generated and is ready to be shared.",
                                                link));
        }

        /**
         * Obtém informações de um payment link
         * 
         * @param linkId ID único do payment link
         * @return DTO com dados do payment link (público, sem autenticação)
         */
        @GetMapping("/payment-link/{linkId}")
        public ResponseEntity<ApiResponse<PaymentLinkDTO>> getPaymentLink(@PathVariable String linkId,
                        HttpServletRequest request) {
                PaymentLinkDTO link = paymentLinkService.getPaymentLink(linkId);
                if (link == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.error(
                                                        "The specified payment link could not be found. It may be invalid or expired.",
                                                        "ERR_PAYMENT_LINK_NOT_FOUND"));
                }
                return ResponseEntity.ok(ApiResponse.success("Payment link details successfully fetched.", link));
        }

        /**
         * Confirma o pagamento de um payment link
         * Valida a transação na blockchain antes de confirmar
         * 
         * @param linkId ID do payment link
         * @param req    DTO com TXID e endereço de origem
         * @return DTO com payment link atualizado (status: "paid")
         */
        @PostMapping("/payment-link/{linkId}/confirm")
        public ResponseEntity<ApiResponse<PaymentLinkDTO>> confirmPayment(@PathVariable String linkId,
                        @RequestBody ConfirmPaymentRequest req,
                        HttpServletRequest request) {
                PaymentLinkDTO link = paymentLinkService.confirmPayment(linkId, req.getTxid(), req.getFromAddress());
                return ResponseEntity.ok(ApiResponse.success(
                                "Payment confirmed successfully. The transaction is now being monitored on the blockchain.",
                                link));
        }

        /**
         * Completa/libera um payment link já pago
         * Só funciona se o payment link está com status "paid"
         * Requer autenticação e que o usuário seja o dono do link
         * 
         * @param linkId ID do payment link
         * @return DTO com payment link atualizado (status: "completed")
         */
        @PostMapping("/payment-link/{linkId}/complete")
        public ResponseEntity<ApiResponse<PaymentLinkDTO>> completePayment(@PathVariable String linkId,
                        Authentication auth) {
                Long userId = getAuthenticatedUserId(auth);
                PaymentLinkDTO link = paymentLinkService.getPaymentLink(linkId);
                if (link == null || !link.getUserId().equals(userId)) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.error(
                                                        "The specified payment link could not be found or you do not have permission to access it.",
                                                        "ERR_PAYMENT_LINK_NOT_FOUND"));
                }
                PaymentLinkDTO completed = paymentLinkService.completePayment(linkId);
                return ResponseEntity
                                .ok(ApiResponse.success("The payment link has been successfully marked as completed.",
                                                completed));
        }

        /**
         * Lista todos os payment links do usuário autenticado
         * 
         * @return Lista de payment links (pending, paid, expired, completed)
         */
        @GetMapping("/payment-links")
        public ResponseEntity<ApiResponse<List<PaymentLinkDTO>>> getUserPaymentLinks(Authentication auth) {
                Long userId = getAuthenticatedUserId(auth);
                List<PaymentLinkDTO> links = paymentLinkService.getUserPaymentLinks(userId);
                return ResponseEntity.ok(ApiResponse.success("Successfully retrieved all your payment links.", links));
        }

        // ==================== WITHDRAWAL ENDPOINTS ====================

        /**
         * Executa um saque de Bitcoin da plataforma para um endereço externo
         * O valor é deduzido do ledger interno do usuário antes do envio on-chain
         * 
         * @param req DTO com endereço de destino, valor e carteira de origem
         * @return DTO com TXID da transação na blockchain
         */
        @PostMapping("/withdraw")
        public ResponseEntity<ApiResponse<TransactionResponseDTO>> withdraw(@RequestBody WithdrawRequestDTO req,
                        Authentication auth) {
                Long userId = getAuthenticatedUserId(auth);
                TransactionResponseDTO response = service.withdraw(userId, req);
                return ResponseEntity.ok(ApiResponse.success(
                                "Withdrawal request processed successfully. Your funds are being sent to protected external address.",
                                response));
        }

        // ==================== UTILITY METHODS ====================

        /**
         * Extrai o ID do usuário autenticado do JWT token
         * 
         * @return ID do usuário extraído do principal do SecurityContext
         */
        private Long getAuthenticatedUserId(Authentication auth) {
                return Long.parseLong(auth.getName());
        }
}
