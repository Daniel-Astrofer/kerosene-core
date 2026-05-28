package source.config.websocket.inbound;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.ledger.application.paymentrequest.InternalPaymentRequestStore;

@Component
public class StompInboundMessageHandlerChain implements StompMessageHandler {

    private final StompMessageHandler chain;

    public StompInboundMessageHandlerChain(
            JwtServicer jwtServicer,
            InternalPaymentRequestStore paymentRequestStore) {
        StompTokenResolver tokenResolver = new NativeHeaderStompTokenResolver(null);

        this.chain = new ConnectAuthenticationStompMessageHandler(
                jwtServicer,
                tokenResolver,
                new SubscribeAuthorizationStompMessageHandler(
                        paymentRequestStore,
                        new CommandLoggingStompMessageHandler(null)));
    }

    @Override
    public Message<?> handle(StompMessageContext context) {
        return chain.handle(context);
    }
}
