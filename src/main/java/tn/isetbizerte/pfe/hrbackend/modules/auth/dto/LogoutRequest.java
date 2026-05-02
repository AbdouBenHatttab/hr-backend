package tn.isetbizerte.pfe.hrbackend.modules.auth.dto;

/**
 * DTO for logout requests.
 */
public class LogoutRequest {
    private String refreshToken;

    public LogoutRequest() {}

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
