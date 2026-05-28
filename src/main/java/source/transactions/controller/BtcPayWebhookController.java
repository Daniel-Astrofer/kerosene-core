package source.transactions.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.transactions.service.BtcPayWebhookService;

@RestController
@RequestMapping("/integrations/btcpay")
@ConditionalOnProperty(prefix = "btcpay", name = "enabled", havingValue = "true")
public class BtcPayWebhookController {

    private final BtcPayWebhookService webhookService;

    public BtcPayWebhookController(BtcPayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook/{storeId}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String storeId,
            @RequestHeader(value = "BTCPAY-SIG", required = false) String signature,
            @RequestBody String rawBody) {
        webhookService.handleWebhook(storeId, signature, rawBody);
        return ResponseEntity.noContent().build();
    }
}
