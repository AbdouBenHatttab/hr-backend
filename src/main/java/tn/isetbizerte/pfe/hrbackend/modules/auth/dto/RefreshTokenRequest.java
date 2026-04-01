package tn.isetbizerte.pfe.hrbackend.modules.auth.dto;

/**
 * DTO for refresh token requests.
 */
public class RefreshTokenRequest {
    private String refreshToken;

    public RefreshTokenRequest() {}

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
