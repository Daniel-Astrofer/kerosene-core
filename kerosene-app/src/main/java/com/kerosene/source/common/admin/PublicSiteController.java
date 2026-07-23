package source.common.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicSiteController {

    private final MobileDownloadService mobileDownloadService;

    public PublicSiteController(MobileDownloadService mobileDownloadService) {
        this.mobileDownloadService = mobileDownloadService;
    }

    @GetMapping("/mobile-download")
    public MobileDownloadService.MobileReleaseInfo mobileDownload() {
        return mobileDownloadService.releaseInfo();
    }
}
