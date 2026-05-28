package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletEntity;
import source.bitcoinaccounts.model.ColdWalletUtxoEntity;
import source.bitcoinaccounts.model.PsbtWorkflowEntity;
import source.bitcoinaccounts.repository.ColdWalletUtxoRepository;
import source.bitcoinaccounts.repository.PsbtWorkflowRepository;
import source.common.service.AddressDerivationService;
import source.transactions.infra.BitcoinCoreRpcClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PsbtWorkflowService {

    private static final BigDecimal SATOSHIS_PER_BTC = new BigDecimal("100000000");

    private final BitcoinAccountService accountService;
    private final ColdWalletUtxoRepository utxoRepository;
    private final PsbtWorkflowRepository workflowRepository;
    private final BitcoinAccountAuditService auditService;
    private final AddressDerivationService addressDerivationService;
    private final BitcoinCoreRpcClient bitcoinCoreRpcClient;
    private final NetworkParameters networkParameters;
    private final long feeSafetyBufferSats;
    private final int feeSafetyMultiplier;
    private final long workflowTtlHours;

    public PsbtWorkflowService(
            BitcoinAccountService accountService,
            ColdWalletUtxoRepository utxoRepository,
            PsbtWorkflowRepository workflowRepository,
            BitcoinAccountAuditService auditService,
            AddressDerivationService addressDerivationService,
            ObjectProvider<BitcoinCoreRpcClient> bitcoinCoreRpcClient,
            @Value("${bitcoin.network:mainnet}") String bitcoinNetwork,
            @Value("${bitcoin-accounts.psbt.fee-safety-buffer-sats:5000}") long feeSafetyBufferSats,
            @Value("${bitcoin-accounts.psbt.fee-safety-multiplier:2}") int feeSafetyMultiplier,
            @Value("${bitcoin-accounts.psbt.workflow-ttl-hours:24}") long workflowTtlHours) {
        this.accountService = accountService;
        this.utxoRepository = utxoRepository;
        this.workflowRepository = workflowRepository;
        this.auditService = auditService;
        this.addressDerivationService = addressDerivationService;
        this.bitcoinCoreRpcClient = bitcoinCoreRpcClient.getIfAvailable();
        this.networkParameters = resolveNetworkParameters(bitcoinNetwork);
        this.feeSafetyBufferSats = Math.max(1_000L, feeSafetyBufferSats);
        this.feeSafetyMultiplier = Math.max(2, feeSafetyMultiplier);
        this.workflowTtlHours = Math.max(1L, workflowTtlHours);
    }

    @Transactional
    public Map<String, Object> createUnsigned(
            Long userId,
            UUID coldWalletId,
            String destinationAddress,
            long amountSats,
            long feeRate,
            List<UUID> selectedUtxoIds) {
        if (bitcoinCoreRpcClient == null) {
            throw new IllegalStateException("Bitcoin Core RPC is required to create watch-only PSBTs.");
        }
        if (destinationAddress == null || destinationAddress.isBlank()) {
            throw new IllegalArgumentException("Destination address is required.");
        }
        requireValidNetworkAddress(destinationAddress.trim());
        if (amountSats <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        ColdWalletEntity wallet = accountService.requireOwnedColdWallet(userId, coldWalletId);
        List<ColdWalletUtxoEntity> selected = selectUtxos(wallet.getId(), amountSats, selectedUtxoIds);
        List<BitcoinCoreRpcClient.PsbtInput> inputs = selected.stream()
                .map(utxo -> new BitcoinCoreRpcClient.PsbtInput(utxo.getTxid(), utxo.getVout()))
                .toList();

        BitcoinCoreRpcClient.FundedPsbt fundedPsbt = bitcoinCoreRpcClient.createWatchOnlyPsbt(
                inputs,
                destinationAddress.trim(),
                amountSats,
                3,
                feeRate > 0L ? feeRate : null);
        if (fundedPsbt.psbt() == null || fundedPsbt.psbt().isBlank()) {
            throw new IllegalStateException("Bitcoin Core did not return a PSBT.");
        }

        selected.forEach(utxo -> utxo.setStatus(BitcoinAccountEnums.UtxoStatus.LOCKED));
        utxoRepository.saveAll(selected);

        PsbtWorkflowEntity workflow = new PsbtWorkflowEntity();
        workflow.setColdWalletId(wallet.getId());
        workflow.setUnsignedPsbt(fundedPsbt.psbt());
        workflow.setDestinationAddress(destinationAddress.trim());
        workflow.setAmountSats(amountSats);
        workflow.setDestinationOutputsHash(sha256(destinationAddress.trim() + "|" + amountSats));
        workflow.setSelectedInputsHash(selectedInputsHash(selected));
        workflow.setSelectedOutpoints(selectedOutpoints(selected));
        workflow.setFeeRate(Math.max(0L, feeRate));
        workflow.setEstimatedFeeSats(fundedPsbt.feeSats());
        workflow.setExpiresAt(LocalDateTime.now().plusHours(workflowTtlHours));
        workflow.setStatus(BitcoinAccountEnums.PsbtStatus.WAITING_EXTERNAL_SIGNATURE);
        workflow = workflowRepository.save(workflow);

        auditService.recordUser(userId, "PSBT_UNSIGNED_CREATED", "PSBT_WORKFLOW",
                workflow.getId().toString(), Map.of("coldWalletId", wallet.getId().toString()));
        return toView(workflow);
    }

    @Transactional
    public Map<String, Object> submitSigned(Long userId, UUID workflowId, String signedPsbt, boolean broadcast) {
        if (bitcoinCoreRpcClient == null) {
            throw new IllegalStateException("Bitcoin Core RPC is required to validate watch-only PSBTs.");
        }
        if (signedPsbt == null || signedPsbt.isBlank()) {
            throw new IllegalArgumentException("Signed PSBT is required.");
        }
        PsbtWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("PSBT workflow not found."));
        ColdWalletEntity wallet = accountService.requireOwnedColdWallet(userId, workflow.getColdWalletId());
        if (workflow.getExpiresAt() != null && workflow.getExpiresAt().isBefore(LocalDateTime.now())) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.FAILED_SAFE);
            throw new IllegalArgumentException(
                    "Esta PSBT expirou por segurança. Gere uma nova intenção de transação.");
        }

        JsonNode decoded = bitcoinCoreRpcClient.decodePsbt(signedPsbt);
        if (!workflow.getSelectedInputsHash().equals(decodedInputsHash(decoded))) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.REJECTED_TAMPERED);
            throw new IllegalArgumentException(
                    "Não transmitimos essa transação porque os inputs não correspondem à intenção original.");
        }
        if (!hasExpectedDestination(decoded, workflow.getDestinationAddress(), workflow.getAmountSats())) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.REJECTED_TAMPERED);
            throw new IllegalArgumentException(
                    "Não transmitimos essa transação porque o destino ou valor não corresponde à intenção original.");
        }
        String changeOutputHash = validatedChangeOutputHash(decoded, wallet, workflow);
        if ("REJECT".equals(changeOutputHash)) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.REJECTED_POLICY);
            throw new IllegalArgumentException(
                    "Essa PSBT possui output desconhecido. Gere uma nova assinatura com destino e troco esperado.");
        }
        if (feeUnsafe(decoded, workflow.getEstimatedFeeSats())) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.REJECTED_POLICY);
            throw new IllegalArgumentException(
                    "A taxa não pôde ser validada dentro do limite de segurança. Revise e gere uma nova PSBT.");
        }

        workflow.setSignedPsbt(signedPsbt);
        workflow.setChangeOutputHash(changeOutputHash);
        workflow.setStatus(BitcoinAccountEnums.PsbtStatus.VALIDATED);
        if (broadcast) {
            BitcoinCoreRpcClient.FinalizedPsbt finalized = bitcoinCoreRpcClient.finalizePsbt(signedPsbt);
            if (!finalized.complete() || finalized.hex() == null || finalized.hex().isBlank()) {
                markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.FAILED_SAFE);
                throw new IllegalArgumentException(
                        "Não conseguimos finalizar essa assinatura com segurança. Gere uma nova assinatura e tente novamente.");
            }
            String txid = bitcoinCoreRpcClient.sendRawTransaction(finalized.hex());
            if (txid == null || txid.isBlank()) {
                markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.FAILED_SAFE);
                throw new IllegalArgumentException(
                        "Não recebemos confirmação de transmissão do Bitcoin Core. Tente novamente após a sincronização.");
            }
            workflow.setBroadcastTxid(txid);
            workflow.setStatus(BitcoinAccountEnums.PsbtStatus.BROADCASTED);
        }
        workflow = workflowRepository.save(workflow);
        auditService.recordUser(userId, broadcast ? "PSBT_BROADCASTED" : "PSBT_VALIDATED", "PSBT_WORKFLOW",
                workflow.getId().toString(), Map.of("status", workflow.getStatus().name()));
        return toView(workflow);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForColdWallet(Long userId, UUID coldWalletId) {
        ColdWalletEntity wallet = accountService.requireOwnedColdWallet(userId, coldWalletId);
        return workflowRepository.findTop100ByColdWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUtxos(Long userId, UUID coldWalletId) {
        ColdWalletEntity wallet = accountService.requireOwnedColdWallet(userId, coldWalletId);
        return utxoRepository.findByColdWalletId(wallet.getId()).stream()
                .sorted(Comparator.comparing(ColdWalletUtxoEntity::getStatus)
                        .thenComparing(Comparator.comparingLong(ColdWalletUtxoEntity::getAmountSats).reversed()))
                .map(this::utxoView)
                .toList();
    }

    @Transactional
    public void expirePendingWorkflows() {
        List<BitcoinAccountEnums.PsbtStatus> expirable = List.of(
                BitcoinAccountEnums.PsbtStatus.WAITING_EXTERNAL_SIGNATURE,
                BitcoinAccountEnums.PsbtStatus.UNSIGNED_CREATED,
                BitcoinAccountEnums.PsbtStatus.VALIDATED);
        for (PsbtWorkflowEntity workflow : workflowRepository
                .findTop200ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(expirable, LocalDateTime.now())) {
            markRejectedAndUnlock(workflow, BitcoinAccountEnums.PsbtStatus.FAILED_SAFE);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long userId, UUID workflowId) {
        PsbtWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("PSBT workflow not found."));
        accountService.requireOwnedColdWallet(userId, workflow.getColdWalletId());
        return toView(workflow);
    }

    private List<ColdWalletUtxoEntity> selectUtxos(UUID coldWalletId, long amountSats, List<UUID> selectedUtxoIds) {
        List<ColdWalletUtxoEntity> available = utxoRepository
                .findForUpdateByColdWalletIdAndStatus(coldWalletId, BitcoinAccountEnums.UtxoStatus.UNSPENT);
        if (selectedUtxoIds != null && !selectedUtxoIds.isEmpty()) {
            available = available.stream()
                    .filter(utxo -> selectedUtxoIds.contains(utxo.getId()))
                    .toList();
        }
        long total = 0L;
        java.util.ArrayList<ColdWalletUtxoEntity> selected = new java.util.ArrayList<>();
        for (ColdWalletUtxoEntity utxo : available.stream()
                .sorted(Comparator.comparingLong(ColdWalletUtxoEntity::getAmountSats))
                .toList()) {
            selected.add(utxo);
            total = Math.addExact(total, utxo.getAmountSats());
            if (total >= amountSats) {
                return selected;
            }
        }
        throw new IllegalArgumentException("Saldo observado insuficiente para montar essa PSBT.");
    }

    private boolean hasExpectedDestination(JsonNode decoded, String destinationAddress, long amountSats) {
        for (JsonNode output : decoded.path("tx").path("vout")) {
            String address = outputAddress(output);
            long outputSats = btcToSats(output.path("value"));
            if (destinationAddress.equals(address) && outputSats == amountSats) {
                return true;
            }
        }
        return false;
    }

    private String validatedChangeOutputHash(JsonNode decoded, ColdWalletEntity wallet, PsbtWorkflowEntity workflow) {
        ArrayList<String> changeOutputs = new ArrayList<>();
        for (JsonNode output : decoded.path("tx").path("vout")) {
            String address = outputAddress(output);
            long amountSats = btcToSats(output.path("value"));
            if (workflow.getDestinationAddress().equals(address) && amountSats == workflow.getAmountSats()) {
                continue;
            }
            if (address == null || address.isBlank() || amountSats <= 0L) {
                return "REJECT";
            }
            if (!isKnownOrAllowedChangeAddress(wallet, address)) {
                return "REJECT";
            }
            changeOutputs.add(address + "|" + amountSats);
        }
        if (changeOutputs.size() > 1) {
            return "REJECT";
        }
        return changeOutputs.isEmpty() ? null : sha256(changeOutputs.get(0));
    }

    private boolean feeUnsafe(JsonNode decoded, long estimatedFeeSats) {
        if (estimatedFeeSats <= 0L) {
            return true;
        }
        JsonNode fee = decoded.path("fee");
        if (fee.isMissingNode() || fee.isNull()) {
            return true;
        }
        long feeSats = btcToSats(fee);
        if (feeSats <= 0L) {
            return true;
        }
        long maxFee = Math.max(estimatedFeeSats + feeSafetyBufferSats, estimatedFeeSats * feeSafetyMultiplier);
        return feeSats > maxFee;
    }

    private String decodedInputsHash(JsonNode decoded) {
        java.util.ArrayList<String> inputs = new java.util.ArrayList<>();
        for (JsonNode input : decoded.path("tx").path("vin")) {
            inputs.add(input.path("txid").asText("") + ":" + input.path("vout").asInt(-1));
        }
        inputs.sort(String::compareTo);
        return sha256(String.join("|", inputs));
    }

    private String selectedInputsHash(List<ColdWalletUtxoEntity> selected) {
        return sha256(selected.stream()
                .map(utxo -> utxo.getTxid() + ":" + utxo.getVout())
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse(""));
    }

    private String selectedOutpoints(List<ColdWalletUtxoEntity> selected) {
        return selected.stream()
                .map(utxo -> utxo.getTxid() + ":" + utxo.getVout())
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private long btcToSats(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return 0L;
        }
        BigDecimal btc = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText("0"));
        return btc.multiply(SATOSHIS_PER_BTC).setScale(0, RoundingMode.DOWN).longValue();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void markRejectedAndUnlock(PsbtWorkflowEntity workflow, BitcoinAccountEnums.PsbtStatus status) {
        workflow.setStatus(status);
        workflowRepository.save(workflow);
        unlockSelectedOutpoints(workflow);
    }

    private void unlockSelectedOutpoints(PsbtWorkflowEntity workflow) {
        String outpoints = workflow.getSelectedOutpoints();
        if (outpoints == null || outpoints.isBlank()) {
            return;
        }
        for (String outpoint : outpoints.split("\\|")) {
            String[] parts = outpoint.split(":");
            if (parts.length != 2) {
                continue;
            }
            int vout;
            try {
                vout = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            utxoRepository.findByColdWalletIdAndTxidAndVout(workflow.getColdWalletId(), parts[0], vout)
                    .filter(utxo -> utxo.getStatus() == BitcoinAccountEnums.UtxoStatus.LOCKED)
                    .ifPresent(utxo -> {
                        utxo.setStatus(BitcoinAccountEnums.UtxoStatus.UNSPENT);
                        utxoRepository.save(utxo);
                    });
        }
    }

    private boolean isKnownOrAllowedChangeAddress(ColdWalletEntity wallet, String address) {
        if (wallet.getXpub() == null || wallet.getXpub().isBlank()) {
            return false;
        }
        try {
            for (int index = 0; index < 100; index++) {
                if (address.equals(addressDerivationService.deriveAddressFromXpub(wallet.getXpub(), index, true))) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }
        return false;
    }

    private String outputAddress(JsonNode output) {
        String address = output.path("scriptPubKey").path("address").asText("");
        if (!address.isBlank()) {
            return address;
        }
        JsonNode addresses = output.path("scriptPubKey").path("addresses");
        return addresses.isArray() && !addresses.isEmpty() ? addresses.get(0).asText("") : "";
    }

    private void requireValidNetworkAddress(String address) {
        try {
            Address.fromString(networkParameters, address);
        } catch (Exception exception) {
            throw new IllegalArgumentException("O endereço de destino não pertence à rede Bitcoin configurada.");
        }
    }

    private NetworkParameters resolveNetworkParameters(String bitcoinNetwork) {
        String normalized = bitcoinNetwork != null ? bitcoinNetwork.toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "main", "mainnet" -> MainNetParams.get();
            case "regtest" -> RegTestParams.get();
            default -> TestNet3Params.get();
        };
    }

    private Map<String, Object> utxoView(ColdWalletUtxoEntity utxo) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", utxo.getId());
        view.put("txidRef", shortRef(utxo.getTxid()));
        view.put("vout", utxo.getVout());
        view.put("amountSats", utxo.getAmountSats());
        view.put("confirmations", utxo.getConfirmations());
        view.put("status", utxo.getStatus());
        return view;
    }

    private String shortRef(String value) {
        if (value == null || value.length() <= 16) {
            return value;
        }
        return value.substring(0, 8) + "..." + value.substring(value.length() - 8);
    }

    private Map<String, Object> toView(PsbtWorkflowEntity workflow) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", workflow.getId());
        view.put("coldWalletId", workflow.getColdWalletId());
        view.put("unsignedPsbt", workflow.getUnsignedPsbt());
        view.put("status", workflow.getStatus());
        view.put("destinationAddress", workflow.getDestinationAddress());
        view.put("amountSats", workflow.getAmountSats());
        view.put("estimatedFeeSats", workflow.getEstimatedFeeSats());
        view.put("broadcastTxid", workflow.getBroadcastTxid());
        view.put("broadcastTxidRef", shortRef(workflow.getBroadcastTxid()));
        view.put("expiresAt", workflow.getExpiresAt());
        view.put("createdAt", workflow.getCreatedAt());
        return view;
    }
}
