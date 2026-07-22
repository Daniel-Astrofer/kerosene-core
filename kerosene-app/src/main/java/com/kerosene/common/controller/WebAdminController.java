package source.common.controller;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebAdminController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping(value = { "/bitcoin-banking", "/bitcoin-banking/**", "/admin", "/admin/**", "/download",
            "/status" }, produces = MediaType.TEXT_HTML_VALUE)
    public String webRoutes() {
        return "forward:/index.html";
    }
}
