package source.treasury.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.treasury.dto.TreasuryOverviewDTO;
import source.treasury.service.TreasuryService;

@RestController
@RequestMapping("/treasury")
public class TreasuryController {

    private final TreasuryService treasuryService;

    public TreasuryController(TreasuryService treasuryService) {
        this.treasuryService = treasuryService;
    }

    @GetMapping("/overview")
    public ResponseEntity<TreasuryOverviewDTO> overview() {
        return ResponseEntity.ok(treasuryService.overview());
    }
}
