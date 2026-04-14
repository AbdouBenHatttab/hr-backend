package tn.isetbizerte.pfe.hrbackend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class ChangePasswordWithOldPasswordRequest {

    @NotBlank
    private String oldPassword;

    @NotBlank
    private String newPassword;

    public ChangePasswordWithOldPasswordRequest() {}

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

