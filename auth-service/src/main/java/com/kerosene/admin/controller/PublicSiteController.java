package com.kerosene.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.admin.service.MobileDownloadService;

/** Publishes public website metadata that requires no administrator authentication. */
@RestController
@RequestMapping("/api/public")
public class PublicSiteController {

    /** Source of mobile release versions and download links. */
    private final MobileDownloadService mobileDownloadService;

    /**
     * Creates the public site controller with the mobile release provider.
     *
     * @param mobileDownloadService release metadata source
     */
    public PublicSiteController(MobileDownloadService mobileDownloadService) {
        this.mobileDownloadService = mobileDownloadService;
    }

    /**
     * Returns mobile application download metadata for the public site.
     *
     * @return current mobile release information
     */
    @GetMapping("/mobile-download")
    public MobileDownloadService.MobileReleaseInfo mobileDownload() {
        return mobileDownloadService.releaseInfo();
    }
}
