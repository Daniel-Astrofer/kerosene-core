package source.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupTotpVerifyRequestDTO {

    @NotBlank(message = "Signup sessionId required")
    private String sessionId;

    private String totpCode;

    public SignupTotpVerifyRequestDTO() {
    }

    @JsonCreator
    public SignupTotpVerifyRequestDTO(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("totpCode") String totpCode) {
        this.sessionId = sessionId;
        this.totpCode = totpCode;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTotpCode() {
        return totpCode;
    }

    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }
}
