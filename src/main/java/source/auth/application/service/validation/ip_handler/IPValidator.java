package source.auth.application.service.validation.ip_handler;

import source.auth.application.service.validation.ip_handler.contracts.IP;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component("IPValidator")
public class IPValidator implements IP {

    @Override
    public String getIP(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if ("X-Forwarded-For".equalsIgnoreCase(header)) {
                    return ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isEmpty()) {
            return remoteAddr;
        }

        return "127.0.0.1";
    }

    @Override
    public String getDeviceHash(HttpServletRequest request) {
        return "OBSOLETE";
    }
}
