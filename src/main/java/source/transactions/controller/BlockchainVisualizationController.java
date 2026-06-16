package source.transactions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.transactions.monitoring.BitcoinBlockchainMonitorService;
import source.transactions.monitoring.LightningNetworkMonitorService;
import source.transactions.service.BlockchainMonitorService;

import java.util.Map;

@RestController
@RequestMapping("/transactions/visualization")
public class BlockchainVisualizationController {

    private final BlockchainMonitorService blockchainMonitorService;

    public BlockchainVisualizationController(BlockchainMonitorService blockchainMonitorService) {
        this.blockchainMonitorService = blockchainMonitorService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BlockchainMonitorService.BlockchainVisualizationSnapshot>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                "Blockchain and Lightning visualization snapshot retrieved.",
                blockchainMonitorService.visualizationSnapshot()));
    }

    @GetMapping("/blockchain")
    public ResponseEntity<ApiResponse<BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot>> blockchain() {
        return ResponseEntity.ok(ApiResponse.success(
                "Blockchain visualization snapshot retrieved.",
                blockchainMonitorService.blockchainStatus()));
    }

    @GetMapping("/lightning")
    public ResponseEntity<ApiResponse<LightningNetworkMonitorService.LightningMonitorSnapshot>> lightning() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lightning visualization snapshot retrieved.",
                blockchainMonitorService.lightningStatus()));
    }

    @PostMapping("/blockchain/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerBlockchainSync() {
        return ResponseEntity.ok(ApiResponse.success(
                "Blockchain sync/search trigger submitted.",
                blockchainMonitorService.triggerBlockchainSync()));
    }
}
