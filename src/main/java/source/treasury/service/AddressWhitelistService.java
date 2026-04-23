package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.treasury.application.port.out.AuditAddressPort;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 🧾 COFRE DE ENDEREÇOS (Whitelist vs XPUB Attack)
 * ─────────────────────────────────────────────────────────────
 * Em vez de manter a XPUB em texto claro no Postgres (Vazamento de Privacidade),
 * o sistema consome endereços pre-gerados e assinados ("White-labeled").
 *
 * Isso mitiga o ataque de "Derivação Parental" se uma chave privada de um
 * endereço de lucro for roubada.
 */
@Service
public class AddressWhitelistService implements AuditAddressPort {

    private static final Logger log = LoggerFactory.getLogger(AddressWhitelistService.class);

    // Lista de endereços autorizados para receber lucro.
    // Em produção, isso seria preenchido pelo orquestrador offline.
    private final ConcurrentLinkedQueue<String> reservedAddresses = new ConcurrentLinkedQueue<>();

    public AddressWhitelistService() {
        // Mocked Addresses: bc1...
        reservedAddresses.add("bc1qawv66l7uuk3035f8q5xskfmsul64hsqz2ay77x");
        reservedAddresses.add("bc1q7w0unm2q7x3h9r7p7v2f8r7p7v2f8r7p7v2f8r");
    }

    /**
     * Consome um endereço da Whitelist para a próxima transação de lucro.
     * @return O endereço destinatário.
     */
    @Override
    public String getNextAuditAddress() {
        String address = reservedAddresses.poll();
        if (address == null) {
            log.error("[CRITICAL] PROFIT WHITELIST DEPLETED! No audit addresses available.");
            // Panic Mode trigger?
            return "PANIC_RECOVERY_ADDRESS_PLACEHOLDER";
        }
        log.info("[Whitelist] Address consumed for audit: {}", address);
        return address;
    }

    /**
     * Adiciona um novo lote de endereços (carregado do Vault).
     */
    @Override
    public void replenishWhitelist(List<String> newAddresses) {
        reservedAddresses.addAll(newAddresses);
    }
}
