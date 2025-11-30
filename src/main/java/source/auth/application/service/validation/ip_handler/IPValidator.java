package source.auth.application.service.validation.ip_handler;

import source.auth.application.service.validation.ip_handler.contracts.IP;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component("IPValidator")
public class IPValidator implements IP {


    @Override
    public String getIP(HttpServletRequest request) {
        String ip = "null";
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
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {

            ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {

                if ("X-Forwarded-for".equalsIgnoreCase(header)) {
                    ip = ip.split(",")[0];
                }

                break;

            }

        }
        return ip;

    }

    @Override
    public String getDeviceHash(HttpServletRequest request) {
        String data = request.getHeader("X-Device-Hash");

        if (!data.isEmpty() && !data.equalsIgnoreCase("unknown")) {
            return data;

        }
        return "NOT FOUND";
    }
}
