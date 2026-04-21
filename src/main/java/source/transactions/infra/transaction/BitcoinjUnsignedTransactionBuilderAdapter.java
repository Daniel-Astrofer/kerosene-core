package source.transactions.infra.transaction;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.UnsignedTransactionBuilderPort;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.exception.TransactionExceptions;
import source.transactions.infra.BlockchainClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class BitcoinjUnsignedTransactionBuilderAdapter implements UnsignedTransactionBuilderPort {

    private static final BigDecimal SATOSHIS_PER_BTC = new BigDecimal("100000000");

    private final BlockchainClient blockchainClient;
    private final NetworkParameters networkParameters;

    public BitcoinjUnsignedTransactionBuilderAdapter(
            BlockchainClient blockchainClient,
            @Value("${bitcoin.network:testnet}") String bitcoinNetwork) {
        this.blockchainClient = blockchainClient;
        this.networkParameters = resolveNetworkParameters(bitcoinNetwork);
    }

    @Override
    public UnsignedTransactionDTO build(TransactionRequestDTO request) {
        validateRequest(request);

        Address destinationAddress = parseAddress(request.getToAddress(), "destination");
        Address changeAddress = parseAddress(request.getFromAddress(), "source");
        long amountSats = btcToSats(request.getAmount());
        long feeSats = request.getFeeSatoshis() != null ? request.getFeeSatoshis() : 0L;
        long requiredSats = Math.addExact(amountSats, feeSats);

        List<BlockchainClient.AddressUtxo> selectedUtxos = selectUtxos(request.getFromAddress(), requiredSats);
        long selectedTotalSats = selectedUtxos.stream()
                .mapToLong(BlockchainClient.AddressUtxo::valueSats)
                .sum();
        long changeSats = selectedTotalSats - requiredSats;

        Transaction transaction = new Transaction(networkParameters);
        for (BlockchainClient.AddressUtxo utxo : selectedUtxos) {
            TransactionOutPoint outPoint = new TransactionOutPoint(
                    networkParameters,
                    utxo.vout(),
                    Sha256Hash.wrap(utxo.txid()));
            transaction.addInput(new TransactionInput(
                    networkParameters,
                    transaction,
                    new byte[0],
                    outPoint,
                    Coin.valueOf(utxo.valueSats())));
        }
        transaction.addOutput(Coin.valueOf(amountSats), destinationAddress);
        if (changeSats >= Transaction.MIN_NONDUST_OUTPUT.value) {
            transaction.addOutput(Coin.valueOf(changeSats), changeAddress);
        }

        UnsignedTransactionDTO dto = new UnsignedTransactionDTO();
        dto.setTxId(transaction.getTxId().toString());
        dto.setFromAddress(request.getFromAddress());
        dto.setToAddress(request.getToAddress());
        dto.setTotalAmount(request.getAmount());
        dto.setFee(feeSats);
        dto.setRawTxHex(HexFormat.of().formatHex(transaction.bitcoinSerialize()));
        dto.setInputs(toDtoInputs(selectedUtxos));
        dto.setOutputs(toDtoOutputs(request.getToAddress(), request.getAmount(), request.getFromAddress(), changeSats));
        return dto;
    }

    private List<BlockchainClient.AddressUtxo> selectUtxos(String fromAddress, long requiredSats) {
        List<BlockchainClient.AddressUtxo> availableUtxos = blockchainClient.getUnspentOutputs(fromAddress);
        List<BlockchainClient.AddressUtxo> selectedUtxos = new ArrayList<>();
        long selectedTotalSats = 0L;

        for (BlockchainClient.AddressUtxo utxo : availableUtxos) {
            if (utxo.valueSats() <= 0L) {
                continue;
            }
            selectedUtxos.add(utxo);
            selectedTotalSats += utxo.valueSats();
            if (selectedTotalSats >= requiredSats) {
                return selectedUtxos;
            }
        }

        throw new TransactionExceptions.TransactionBuildFailed(
                "Insufficient confirmed UTXOs to build unsigned transaction.");
    }

    private List<UnsignedTransactionDTO.TransactionInput> toDtoInputs(List<BlockchainClient.AddressUtxo> selectedUtxos) {
        List<UnsignedTransactionDTO.TransactionInput> inputs = new ArrayList<>();
        for (BlockchainClient.AddressUtxo utxo : selectedUtxos) {
            inputs.add(new UnsignedTransactionDTO.TransactionInput(
                    utxo.txid(),
                    utxo.vout(),
                    satsToBtc(utxo.valueSats()),
                    utxo.scriptPubKey()));
        }
        return inputs;
    }

    private List<UnsignedTransactionDTO.TransactionOutput> toDtoOutputs(
            String toAddress,
            BigDecimal amount,
            String fromAddress,
            long changeSats) {
        List<UnsignedTransactionDTO.TransactionOutput> outputs = new ArrayList<>();
        outputs.add(new UnsignedTransactionDTO.TransactionOutput(toAddress, amount));
        if (changeSats >= Transaction.MIN_NONDUST_OUTPUT.value) {
            outputs.add(new UnsignedTransactionDTO.TransactionOutput(fromAddress, satsToBtc(changeSats)));
        }
        return outputs;
    }

    private void validateRequest(TransactionRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction request is required.");
        }
        if (request.getFromAddress() == null || request.getFromAddress().isBlank()) {
            throw new IllegalArgumentException("Source address is required.");
        }
        if (request.getToAddress() == null || request.getToAddress().isBlank()) {
            throw new IllegalArgumentException("Destination address is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive.");
        }
        if (request.getFeeSatoshis() != null && request.getFeeSatoshis() < 0L) {
            throw new IllegalArgumentException("Transaction fee cannot be negative.");
        }
    }

    private Address parseAddress(String address, String label) {
        try {
            return Address.fromString(networkParameters, address);
        } catch (AddressFormatException ex) {
            throw new TransactionExceptions.TransactionBuildFailed(
                    "Invalid Bitcoin " + label + " address for configured network.",
                    ex);
        }
    }

    private long btcToSats(BigDecimal btc) {
        return btc.multiply(SATOSHIS_PER_BTC)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BTC, 8, RoundingMode.HALF_UP);
    }

    private NetworkParameters resolveNetworkParameters(String bitcoinNetwork) {
        String normalized = bitcoinNetwork != null ? bitcoinNetwork.toLowerCase(Locale.ROOT) : "";
        return "mainnet".equals(normalized) ? MainNetParams.get() : TestNet3Params.get();
    }
}
