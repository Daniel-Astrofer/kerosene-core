package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.AbstractStub;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import source.common.infra.logging.LogSanitizer;
import source.common.service.AddressDerivationService;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.infra.LightningPaymentGateway;
import source.transactions.infra.LightningClient;
import source.transactions.infra.lnd.proto.lnrpc.AddInvoiceResponse;
import source.transactions.infra.lnd.proto.lnrpc.ChannelBalanceRequest;
import source.transactions.infra.lnd.proto.lnrpc.ChannelBalanceResponse;
import source.transactions.infra.lnd.proto.lnrpc.GetInfoRequest;
import source.transactions.infra.lnd.proto.lnrpc.GetInfoResponse;
import source.transactions.infra.lnd.proto.lnrpc.GetTransactionsRequest;
import source.transactions.infra.lnd.proto.lnrpc.Invoice;
import source.transactions.infra.lnd.proto.lnrpc.InvoiceSubscription;
import source.transactions.infra.lnd.proto.lnrpc.LightningGrpc;
import source.transactions.infra.lnd.proto.lnrpc.PaymentHash;
import source.transactions.infra.lnd.proto.lnrpc.SendRequest;
import source.transactions.infra.lnd.proto.lnrpc.SendResponse;
import source.transactions.infra.lnd.proto.lnrpc.Transaction;
import source.transactions.infra.lnd.proto.lnrpc.TransactionDetails;
import source.transactions.infra.lnd.proto.lnrpc.WalletBalanceRequest;
import source.transactions.infra.lnd.proto.walletrpc.AddressProperty;
import source.transactions.infra.lnd.proto.walletrpc.ImportPublicKeyRequest;
import source.transactions.infra.lnd.proto.walletrpc.ListAddressesRequest;
import source.transactions.infra.lnd.proto.walletrpc.ListAddressesResponse;
import source.transactions.infra.lnd.proto.walletrpc.WalletKitGrpc;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component("lndLightningGateway")
@ConditionalOnProperty(prefix = "lightning.lnd", name = "enabled", havingValue = "true")
public class BitcoinNodeService implements LightningInvoiceGateway, LightningPaymentGateway, LightningClient, WatchOnlyAddressImportPort {

    private static final Logger log = LoggerFactory.getLogger(BitcoinNodeService.class);
    private static final HexFormat HEX = HexFormat.of();
    private static final String IMPORTED_ACCOUNT_NAME = "imported";

    private final ObjectMapper objectMapper;
    private final AddressDerivationService addressDerivationService;
    private final String host;
    private final int port;
    private final boolean tlsEnabled;
    private final String tlsCertPath;
    private final String macaroonHex;
    private final int paymentTimeoutSeconds;
    private final String providerName;

    private ManagedChannel channel;
    private LightningGrpc.LightningBlockingStub lightningBlockingStub;
    private WalletKitGrpc.WalletKitBlockingStub walletKitBlockingStub;
    private final Map<String, Transaction> transactionCache = new ConcurrentHashMap<>();

    public BitcoinNodeService(
            ObjectMapper objectMapper,
            AddressDerivationService addressDerivationService,
            @Value("${lightning.lnd.host:}") String host,
            @Value("${lightning.lnd.port:10009}") int port,
            @Value("${lightning.lnd.tls.enabled:true}") boolean tlsEnabled,
            @Value("${lightning.lnd.tls.cert-path:}") String tlsCertPath,
            @Value("${lightning.lnd.macaroon:}") String macaroonHex,
            @Value("${lightning.lnd.macaroon-path:}") String macaroonPath,
            @Value("${lightning.lnd.payment-timeout-seconds:30}") int paymentTimeoutSeconds,
            @Value("${lightning.lnd.provider-name:LND_BITCOIND_PRUNED}") String providerName) {
        this.objectMapper = objectMapper;
        this.addressDerivationService = addressDerivationService;
        this.host = host != null ? host.trim() : "";
        this.port = port;
        this.tlsEnabled = tlsEnabled;
        this.tlsCertPath = tlsCertPath != null ? tlsCertPath.trim() : "";
        this.macaroonHex = resolveMacaroonHex(macaroonHex, macaroonPath);
        this.paymentTimeoutSeconds = Math.max(5, paymentTimeoutSeconds);
        this.providerName = providerName != null && !providerName.isBlank()
                ? providerName.trim()
                : "LND_BITCOIND_PRUNED";
    }

    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            throw new IllegalStateException("LND gRPC is enabled but host/macaroon configuration is incomplete.");
        }

        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port)
                .keepAliveWithoutCalls(true);
        if (tlsEnabled) {
            this.channel = builder.sslContext(buildSslContext()).build();
        } else {
            log.warn("[BitcoinNodeService] Plaintext gRPC is enabled only for local development.");
            this.channel = builder.usePlaintext().build();
        }

        ClientInterceptor macaroonInterceptor = metadataInterceptor();
        this.lightningBlockingStub = attach(LightningGrpc.newBlockingStub(channel), macaroonInterceptor);
        this.walletKitBlockingStub = attach(WalletKitGrpc.newBlockingStub(channel), macaroonInterceptor);
        log.info("[BitcoinNodeService] Connected to LND gRPC at {}:{}.", host, port);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isLive() {
        return isConfigured();
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public CustodyGateway.GeneratedLightningInvoice createLightningInvoice(CustodyGateway.LightningInvoiceCommand command) {
        Invoice invoice = Invoice.newBuilder()
                .setMemo(safe(command.memo()))
                .setValue(command.amountSats())
                .setExpiry(Math.max(1, command.expiresInSeconds()))
                .build();
        AddInvoiceResponse response = lightningBlockingStub.addInvoice(invoice);
        String paymentHash = HEX.formatHex(response.getRHash().toByteArray());
        return new CustodyGateway.GeneratedLightningInvoice(
                response.getPaymentRequest(),
                paymentHash,
                null,
                paymentHash,
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(Math.max(1, command.expiresInSeconds())));
    }

    @Override
    public CustodyGateway.IncomingLightningInvoiceStatus getLightningInvoiceStatus(CustodyGateway.LightningInvoiceStatusCommand command) {
        Invoice invoice = lookupInvoice(command.paymentHash());
        return new CustodyGateway.IncomingLightningInvoiceStatus(
                mapInvoiceStatus(invoice),
                invoice.getAmtPaidSat() > 0 ? invoice.getAmtPaidSat() : null,
                invoice.getSettleDate() > 0
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(invoice.getSettleDate()), ZoneOffset.UTC)
                        : null,
                invoiceToJson(invoice).toString());
    }

    @Override
    public boolean cancelLightningInvoice(CustodyGateway.LightningInvoiceCancellationCommand command) {
        return false;
    }

    @Override
    public CustodyGateway.PaymentResult payLightning(CustodyGateway.LightningPaymentCommand command) {
        SendRequest request = SendRequest.newBuilder()
                .setPaymentRequest(command.paymentRequest())
                .setAmt(command.amountSats())
                .setFeeLimit(source.transactions.infra.lnd.proto.lnrpc.FeeLimit.newBuilder()
                        .setFixed(Math.max(0L, command.maxFeeSats()))
                        .build())
                .build();

        SendResponse response = lightningBlockingStub.sendPaymentSync(request);
        if (response.getPaymentError() != null && !response.getPaymentError().isBlank()) {
            throw new IllegalStateException("LND failed to route the Lightning payment: " + response.getPaymentError());
        }

        long feeSats = response.hasPaymentRoute()
                ? Math.max(response.getPaymentRoute().getTotalFees(), response.getPaymentRoute().getTotalFeesMsat() / 1000L)
                : 0L;
        return new CustodyGateway.PaymentResult(
                HEX.formatHex(response.getPaymentHash().toByteArray()),
                null,
                HEX.formatHex(response.getPaymentHash().toByteArray()),
                "SETTLED",
                feeSats,
                response.toString());
    }

    @Override
    public long getLocalBalance() {
        ChannelBalanceResponse response = lightningBlockingStub.channelBalance(ChannelBalanceRequest.newBuilder().build());
        return response.hasLocalBalance()
                ? response.getLocalBalance().getSat()
                : Math.max(0L, response.getBalance());
    }

    @Override
    public long getRemoteBalance() {
        ChannelBalanceResponse response = lightningBlockingStub.channelBalance(ChannelBalanceRequest.newBuilder().build());
        return response.hasRemoteBalance() ? response.getRemoteBalance().getSat() : 0L;
    }

    @Override
    public long getLightningNodeBalance() {
        long local = getLocalBalance();
        long remote = getRemoteBalance();
        return Math.max(0L, local + remote);
    }

    @Override
    public double getNodeUptime() {
        return getInfo().getSyncedToChain() ? 1.0d : 0.0d;
    }

    public GetInfoResponse getInfo() {
        return lightningBlockingStub.getInfo(GetInfoRequest.newBuilder().build());
    }

    @Override
    public long getLspLatency() {
        return 0L;
    }

    public JsonNode executeRpc(String method, Object... params) {
        if (method == null || method.isBlank()) {
            return null;
        }

        return switch (method) {
            case "getrawtransaction" -> {
                String txid = params != null && params.length > 0 ? String.valueOf(params[0]) : null;
                boolean verbose = params != null && params.length > 1 && "1".equals(String.valueOf(params[1]));
                yield getRawTransaction(txid, verbose);
            }
            case "listreceivedbyaddress" -> {
                String address = params != null && params.length > 3 ? String.valueOf(params[3]) : null;
                yield getAddressTransactions(address);
            }
            default -> null;
        };
    }

    public JsonNode getRawTransaction(String txid, boolean verbose) {
        Transaction transaction = findTransaction(txid);
        if (transaction == null) {
            return objectMapper.nullNode();
        }
        return toJson(transaction);
    }

    public long getHotWalletBalance() {
        return Math.max(0L, lightningBlockingStub.walletBalance(WalletBalanceRequest.newBuilder().build()).getConfirmedBalance());
    }

    public JsonNode getAddressTransactions(String address) {
        ArrayNode array = objectMapper.createArrayNode();
        if (address == null || address.isBlank()) {
            return array;
        }

        for (Transaction transaction : listTransactions()) {
            if (transaction.getOutputDetailsList().stream().anyMatch(output -> address.equals(output.getAddress()))) {
                array.add(toJson(transaction));
            }
        }
        return array;
    }

    public long getConfirmedBalanceForAddress(String address) {
        if (address == null || address.isBlank()) {
            return 0L;
        }
        return listAddressBalances().getOrDefault(address, 0L);
    }

    public long getConfirmedBalanceForXpub(String xpub, int range, boolean includeChangeBranch) {
        if (xpub == null || xpub.isBlank()) {
            return 0L;
        }

        List<String> addresses = new ArrayList<>();
        int safeRange = Math.max(1, range);
        for (int index = 0; index < safeRange; index++) {
            addresses.add(addressDerivationService.deriveAddressFromXpub(xpub, index));
            if (includeChangeBranch) {
                addresses.add(addressDerivationService.deriveAddressFromXpub(xpub, index, true));
            }
        }
        return getConfirmedBalanceForDerivedAddresses(addresses);
    }

    public long getConfirmedBalanceForDerivedAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return 0L;
        }
        Map<String, Long> balances = listAddressBalances();
        return addresses.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .mapToLong(address -> balances.getOrDefault(address, 0L))
                .sum();
    }

    public void importWatchOnlyPublicKey(byte[] publicKey, String expectedAddress) {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("A compressed public key is required for watch-only import.");
        }
        try {
            walletKitBlockingStub.importPublicKey(ImportPublicKeyRequest.newBuilder()
                    .setPublicKey(com.google.protobuf.ByteString.copyFrom(publicKey))
                    .setAddressType(source.transactions.infra.lnd.proto.walletrpc.AddressType.WITNESS_PUBKEY_HASH)
                    .build());
            log.info("[BitcoinNodeService] Imported watch-only public key for addressRef={}.",
                    LogSanitizer.fingerprint(expectedAddress));
        } catch (StatusRuntimeException ex) {
            if (isAlreadyExists(ex)) {
                log.debug("[BitcoinNodeService] Watch-only key for addressRef={} was already imported.",
                        LogSanitizer.fingerprint(expectedAddress));
                return;
            }
            throw ex;
        }
    }

    public Iterator<Transaction> subscribeTransactions() {
        return lightningBlockingStub.subscribeTransactions(GetTransactionsRequest.newBuilder().build());
    }

    public Iterator<Invoice> subscribeInvoices(long addIndex, long settleIndex) {
        return lightningBlockingStub.subscribeInvoices(InvoiceSubscription.newBuilder()
                .setAddIndex(Math.max(0L, addIndex))
                .setSettleIndex(Math.max(0L, settleIndex))
                .build());
    }

    public String mapInvoiceStatus(Invoice invoice) {
        Invoice.InvoiceState state = invoice.getState();
        return switch (state) {
            case SETTLED -> "SETTLED";
            case ACCEPTED -> "PENDING";
            case CANCELED -> "CANCELLED";
            case OPEN, UNRECOGNIZED -> isExpired(invoice) ? "EXPIRED" : "PENDING";
        };
    }

    private boolean isExpired(Invoice invoice) {
        if (invoice.getExpiry() <= 0 || invoice.getCreationDate() <= 0) {
            return false;
        }
        return Instant.now().isAfter(Instant.ofEpochSecond(invoice.getCreationDate() + invoice.getExpiry()));
    }

    private Invoice lookupInvoice(String paymentHashHex) {
        byte[] paymentHash = decodeHex(paymentHashHex);
        return lightningBlockingStub.lookupInvoice(PaymentHash.newBuilder()
                .setRHash(com.google.protobuf.ByteString.copyFrom(paymentHash))
                .build());
    }

    private Transaction findTransaction(String txid) {
        if (txid == null || txid.isBlank()) {
            return null;
        }
        Transaction cached = transactionCache.get(txid);
        if (cached != null) {
            return cached;
        }
        for (Transaction transaction : listTransactions()) {
            if (txid.equalsIgnoreCase(transaction.getTxHash())) {
                transactionCache.put(transaction.getTxHash(), transaction);
                return transaction;
            }
        }
        return null;
    }

    private List<Transaction> listTransactions() {
        TransactionDetails details = lightningBlockingStub.getTransactions(GetTransactionsRequest.newBuilder()
                .setStartHeight(0)
                .setEndHeight(-1)
                .setMaxTransactions(0)
                .build());
        List<Transaction> transactions = new ArrayList<>(details.getTransactionsList());
        for (Transaction transaction : transactions) {
            transactionCache.put(transaction.getTxHash(), transaction);
        }
        return transactions;
    }

    private Map<String, Long> listAddressBalances() {
        ListAddressesResponse response = walletKitBlockingStub.listAddresses(ListAddressesRequest.newBuilder()
                .build());
        Map<String, Long> balances = new LinkedHashMap<>();
        for (var account : response.getAccountWithAddressesList()) {
            for (AddressProperty address : account.getAddressesList()) {
                if (address.getAddress() != null && !address.getAddress().isBlank()) {
                    balances.put(address.getAddress(), Math.max(0L, address.getBalance()));
                }
            }
        }
        return balances;
    }

    private ObjectNode toJson(Transaction transaction) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("txid", transaction.getTxHash());
        node.put("confirmations", Math.max(0, transaction.getNumConfirmations()));
        node.put("amount", transaction.getAmount());
        node.put("blockhash", transaction.getBlockHash());
        node.put("blockheight", transaction.getBlockHeight());
        node.put("time", transaction.getTimeStamp());
        ArrayNode outputs = node.putArray("vout");
        transaction.getOutputDetailsList().forEach(output -> outputs.add(outputToJson(output)));
        return node;
    }

    private ObjectNode outputToJson(source.transactions.infra.lnd.proto.lnrpc.OutputDetail output) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("value", output.getAmount());
        node.put("scriptpubkey_address", output.getAddress());
        ObjectNode scriptPubKey = node.putObject("scriptPubKey");
        scriptPubKey.put("address", output.getAddress());
        ArrayNode addresses = scriptPubKey.putArray("addresses");
        if (output.getAddress() != null && !output.getAddress().isBlank()) {
            addresses.add(output.getAddress());
        }
        return node;
    }

    private ObjectNode invoiceToJson(Invoice invoice) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("paymentHash", HEX.formatHex(invoice.getRHash().toByteArray()));
        node.put("paymentRequest", invoice.getPaymentRequest());
        node.put("state", invoice.getState().name());
        node.put("settled", invoice.getSettled());
        node.put("amtPaidSat", invoice.getAmtPaidSat());
        node.put("addIndex", invoice.getAddIndex());
        node.put("settleIndex", invoice.getSettleIndex());
        return node;
    }

    private boolean isConfigured() {
        return !host.isBlank() && port > 0 && !macaroonHex.isBlank();
    }

    private SslContext buildSslContext() {
        File trustCert = requireReadableFile(tlsCertPath, "lightning.lnd.tls.cert-path");
        try {
            return GrpcSslContexts.forClient()
                    .trustManager(trustCert)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to configure TLS for LND gRPC.", ex);
        }
    }

    private ClientInterceptor metadataInterceptor() {
        Metadata metadata = new Metadata();
        Metadata.Key<String> macaroonKey = Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(macaroonKey, macaroonHex);
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }

    private <T extends AbstractStub<T>> T attach(T stub, ClientInterceptor interceptor) {
        return stub.withInterceptors(interceptor);
    }

    private File requireReadableFile(String path, String propertyName) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(propertyName + " must point to a readable file.");
        }
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalStateException(propertyName + " must point to a readable file: " + path);
        }
        return file;
    }

    private boolean isAlreadyExists(StatusRuntimeException ex) {
        return ex.getStatus().getCode() == Status.Code.ALREADY_EXISTS
                || String.valueOf(ex.getStatus().getDescription()).toLowerCase(Locale.ROOT).contains("already exists");
    }

    private String resolveMacaroonHex(String configuredHex, String macaroonPath) {
        String normalized = configuredHex != null ? configuredHex.trim() : "";
        if (!normalized.isBlank()) {
            return normalized;
        }
        if (macaroonPath == null || macaroonPath.isBlank()) {
            return "";
        }
        try {
            return HEX.formatHex(Files.readAllBytes(new File(macaroonPath).toPath()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read lightning.lnd.macaroon-path.", ex);
        }
    }

    private byte[] decodeHex(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return HEX.parseHex(value.trim());
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
