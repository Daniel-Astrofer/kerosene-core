package source.wallet.controller;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.wallet.dto.WalletDTO;
import source.wallet.model.WalletEntity;
import source.wallet.orchestrator.WalletUseCase;

@RestController
@RequestMapping("/wallet")
public class WalletController {
    private final WalletUseCase wallet;

    public WalletController(WalletUseCase wallet) {
        this.wallet = wallet;
    }

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody WalletDTO dto,
                                           HttpServletRequest request){
        wallet.createWallet(dto, request);
        return ResponseEntity.status(HttpStatus.CREATED).body("wallet created");
    }
   /* @GetMapping("/find")
    public ResponseEntity<String> findWallets(@RequestBody WalletDTO dto,
                                              HttpServletRequest request){



    }*/


}
