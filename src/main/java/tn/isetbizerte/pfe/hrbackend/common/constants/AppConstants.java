package tn.isetbizerte.pfe.hrbackend.common.constants;

/**
 * Application-wide constants
 */
public final class AppConstants {

    private AppConstants() {
    }

    public static final String API_BASE_PATH = "/api";
    public static final String PUBLIC_PATH = "/public";

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DEFAULT_TOKEN_EXPIRY_SECONDS = 900;

    public static final String DEFAULT_REALM = "hr-realm";
    public static final String DEFAULT_CLIENT_ID = "hr-backend";
}

