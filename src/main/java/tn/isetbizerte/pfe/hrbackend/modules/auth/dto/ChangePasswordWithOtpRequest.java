package tn.isetbizerte.pfe.hrbackend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class ChangePasswordWithOtpRequest {

    @NotBlank
    private String otp;

    @NotBlank
    private String newPassword;

    public ChangePasswordWithOtpRequest() {}

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

