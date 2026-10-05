package com.kerosene.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.platform.release.ReleaseManifestService;

/** Exposes the current system release manifest as a simple read-only endpoint. */
@RestController
public class SystemReleaseController {

    /** Release manifest snapshot provider. */
    private final ReleaseManifestService releaseManifestService;

    /**
     * Creates the controller with its release manifest service.
     *
     * @param releaseManifestService release metadata provider
     */
    public SystemReleaseController(ReleaseManifestService releaseManifestService) {
        this.releaseManifestService = releaseManifestService;
    }

    /**
     * Returns the current system release snapshot.
     *
     * @return release version, channels, and configured artifact metadata
     */
    @GetMapping("/system/release")
    public ReleaseManifestService.ReleaseSnapshot release() {
        return releaseManifestService.snapshot();
    }
}
