package tn.isetbizerte.pfe.hrbackend.devdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.dev-seed")
public class DevSeedProperties {

    private boolean enabled;
    private boolean reset;
    private String confirm;
    private String usernamePrefix = "seed.";
    private String emailDomain = "seed.arabsoft.local";
    private String password;
    private boolean requireLocalDatabase = true;
    private boolean requireLocalKeycloak = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isReset() {
        return reset;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    public String getConfirm() {
        return confirm;
    }

    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }

    public String getUsernamePrefix() {
        return usernamePrefix;
    }

    public void setUsernamePrefix(String usernamePrefix) {
        this.usernamePrefix = usernamePrefix;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain) {
        this.emailDomain = emailDomain;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRequireLocalDatabase() {
        return requireLocalDatabase;
    }

    public void setRequireLocalDatabase(boolean requireLocalDatabase) {
        this.requireLocalDatabase = requireLocalDatabase;
    }

    public boolean isRequireLocalKeycloak() {
        return requireLocalKeycloak;
    }

    public void setRequireLocalKeycloak(boolean requireLocalKeycloak) {
        this.requireLocalKeycloak = requireLocalKeycloak;
    }
}
