package com.kerosene.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.kerosene.auth.application.usecase.security.AppPinOperationsUseCase;
import com.kerosene.auth.dto.AppPinStatusDTO;
import com.kerosene.auth.dto.ConfigureAppPinRequestDTO;
import com.kerosene.auth.dto.VerifyAppPinRequestDTO;
import com.kerosene.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/security/app-pin")
public class AppPinController {

    private final AppPinOperationsUseCase appPinOperationsUseCase;

    public AppPinController(AppPinOperationsUseCase appPinOperationsUseCase) {
        this.appPinOperationsUseCase = appPinOperationsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> getStatus(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash) {
        AppPinStatusDTO status = appPinOperationsUseCase.getStatus(deviceHash);
        return ResponseEntity.ok(ApiResponse.success("App PIN status retrieved successfully.", status));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> configure(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash,
            @RequestBody ConfigureAppPinRequestDTO request) {
        AppPinStatusDTO status = appPinOperationsUseCase.configure(deviceHash, request);
        return ResponseEntity.ok(ApiResponse.success("App PIN settings updated successfully.", status));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AppPinStatusDTO>> verify(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash,
            @RequestBody VerifyAppPinRequestDTO request) {
        AppPinStatusDTO status = appPinOperationsUseCase.verify(deviceHash, request);
        return ResponseEntity.ok(ApiResponse.success("App PIN verified successfully.", status));
    }
}
