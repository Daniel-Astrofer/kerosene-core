package source.notification.l10n;

import org.junit.jupiter.api.Test;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationMessagesTest {

    @Test
    void shouldResolveLocalizedTransactionMessage() {
        LocalizedNotificationMessage message = NotificationMessages.resolve(
                NotificationMessageKey.INTERNAL_TRANSFER_SENT,
                "0,001",
                "Principal");

        assertEquals("BTC enviado", message.title());
        assertEquals("Você enviou 0,001 BTC da carteira “Principal”.", message.body());
    }

    @Test
    void shouldResolveRequestedNotificationDialogCopy() {
        List<ExpectedNotification> expectedNotifications = List.of(
                new ExpectedNotification(
                        NotificationMessageKey.INTERNAL_TRANSFER_RECEIVED,
                        "BTC recebido",
                        "Você recebeu 0.00100000 BTC na carteira “Principal”.",
                        "0.00100000",
                        "Principal"),
                new ExpectedNotification(
                        NotificationMessageKey.INTERNAL_TRANSFER_SENT,
                        "BTC enviado",
                        "Você enviou 0.00100000 BTC da carteira “Principal”.",
                        "0.00100000",
                        "Principal"),
                new ExpectedNotification(
                        NotificationMessageKey.PAYMENT_REQUEST_CREATED,
                        "Cobrança criada",
                        "Cobrança de 0.00100000 BTC criada para a carteira “Principal”.",
                        "0.00100000",
                        "Principal"),
                new ExpectedNotification(
                        NotificationMessageKey.PAYMENT_REQUEST_PAID,
                        "Cobrança paga",
                        "Sua cobrança de 0.00100000 BTC foi paga com sucesso.",
                        "0.00100000"),
                new ExpectedNotification(
                        NotificationMessageKey.TRANSACTION_BROADCAST_NO_AMOUNT,
                        "Transação enviada",
                        "Sua transação foi enviada para a rede Bitcoin."),
                new ExpectedNotification(
                        NotificationMessageKey.TRANSACTION_BROADCAST_WITH_AMOUNT,
                        "BTC enviado",
                        "Envio de 0.00100000 BTC transmitido à rede Bitcoin.",
                        "0.00100000"),
                new ExpectedNotification(
                        NotificationMessageKey.WALLET_ENTRY_DETECTED,
                        "Entrada identificada",
                        "Nova entrada identificada em sua carteira."),
                new ExpectedNotification(
                        NotificationMessageKey.WALLET_ENTRY_AMOUNT_DETECTED,
                        "BTC identificado",
                        "Entrada de 0.00100000 BTC identificada na carteira “Principal”.",
                        "0.00100000",
                        "Principal"),
                new ExpectedNotification(
                        NotificationMessageKey.WALLET_ENTRY_AMOUNT_MESSAGE_DETECTED,
                        "BTC identificado",
                        "Entrada de 0.00100000 BTC identificada. Mensagem: teste",
                        "0.00100000",
                        "teste"),
                new ExpectedNotification(
                        NotificationMessageKey.PENDING_DEPOSIT_DETECTED,
                        "Depósito pendente",
                        "Depósito de 0.00100000 BTC aguardando confirmações.",
                        "0.00100000"),
                new ExpectedNotification(
                        NotificationMessageKey.NETWORK_TRANSFER_CONFIRMED,
                        "Transferência confirmada",
                        "Transferência de 0.00100000 BTC confirmada na rede Bitcoin.",
                        "0.00100000"),
                new ExpectedNotification(
                        NotificationMessageKey.NETWORK_DEPOSIT_CONFIRMED,
                        "Depósito confirmado",
                        "Depósito de 0.00100000 BTC confirmado. Crédito líquido: 0.00099000 BTC.",
                        "0.00100000",
                        "0.00099000"),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_ONCHAIN_PAYMENT_SENT,
                        "Pagamento enviado",
                        "Pagamento on-chain enviado com sucesso."),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_LIGHTNING_PAYMENT_SENT,
                        "Pagamento enviado",
                        "Pagamento Lightning enviado com sucesso."),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_CONFIRMED,
                        "Depósito confirmado",
                        "Depósito on-chain confirmado. Crédito líquido: 0.00099000 BTC.",
                        "0.00099000"),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_ONCHAIN_DEPOSIT_RECONCILED,
                        "Depósito confirmado",
                        "Depósito on-chain confirmado. Crédito líquido: 0.00099000 BTC.",
                        "0.00099000"),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_CONFIRMED,
                        "Depósito confirmado",
                        "Depósito Lightning liquidado. Crédito líquido: 0.00099000 BTC.",
                        "0.00099000"),
                new ExpectedNotification(
                        NotificationMessageKey.EXTERNAL_LIGHTNING_DEPOSIT_RECONCILED,
                        "Depósito confirmado",
                        "Depósito Lightning liquidado. Crédito líquido: 0.00099000 BTC.",
                        "0.00099000"),
                new ExpectedNotification(
                        NotificationMessageKey.ACCOUNT_CREATED,
                        "Conta criada",
                        "Sua conta Kerosene foi criada com sucesso."),
                new ExpectedNotification(
                        NotificationMessageKey.SECURITY_LOGIN_DETECTED,
                        "Novo acesso detectado",
                        "Identificamos um novo acesso à sua conta Kerosene. Se não reconhece esta atividade, revise suas sessões ativas imediatamente."),
                new ExpectedNotification(
                        NotificationMessageKey.SECURITY_ADMIN_ACCESS_ATTEMPT,
                        "Tentativa de acesso administrativo",
                        "Uma solicitação de acesso ao painel administrativo está aguardando sua revisão. Confira navegador, dispositivo e horário antes de aprovar."),
                new ExpectedNotification(
                        NotificationMessageKey.SECURITY_RECOVERY_COMPLETED,
                        "Recuperação de segurança concluída",
                        "Sua frase de recuperação, TOTP, passkey e códigos de backup foram renovados. Entre novamente usando as novas credenciais."),
                new ExpectedNotification(
                        NotificationMessageKey.DEMO_BALANCE_CREDITED,
                        "Saldo de teste creditado",
                        "Você recebeu 100.00000000 BTC de saldo de teste na carteira “Principal”.",
                        "100.00000000",
                        "Principal"));

        for (ExpectedNotification expected : expectedNotifications) {
            LocalizedNotificationMessage message = NotificationMessages.resolve(
                    expected.key(),
                    expected.args());

            assertEquals(expected.title(), message.title());
            assertEquals(expected.body(), message.body());
        }
    }

    @Test
    void shouldCreateLocalizedPayload() {
        UserNotificationPayload payload = NotificationMessages.payload(
                NotificationKind.DEPOSIT_CONFIRMED,
                NotificationSeverity.SUCCESS,
                NotificationMessageKey.NETWORK_DEPOSIT_CONFIRMED,
                "/deposits",
                "transaction",
                "tx-123",
                Map.of("netAmountBtc", "0.00099000"),
                "0.00100000",
                "0.00099000");

        assertEquals("deposit_confirmed", payload.kind());
        assertEquals("success", payload.severity());
        assertEquals("Depósito confirmado", payload.title());
        assertEquals("Depósito de 0.00100000 BTC confirmado. Crédito líquido: 0.00099000 BTC.", payload.body());
        assertEquals("/deposits", payload.deeplink());
        assertEquals("transaction", payload.entityType());
        assertEquals("tx-123", payload.entityId());
    }

    private record ExpectedNotification(
            NotificationMessageKey key,
            String title,
            String body,
            Object... args) {
    }
}
