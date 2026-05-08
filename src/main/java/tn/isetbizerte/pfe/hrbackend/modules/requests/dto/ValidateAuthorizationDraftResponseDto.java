package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for the dry-run authorization draft validation endpoint.
 *
 * Always returned with HTTP 200.
 * {@code valid=true}  — all rules passed; the draft can proceed to real submission.
 * {@code valid=false} — one or more rules failed; {@code errors} lists every violation.
 *
 * No side effects: this is a pure validation result. Nothing is saved to DB,
 * no Kafka/outbox events are published, and no history records are created.
 */
public class ValidateAuthorizationDraftResponseDto {

    /** Whether the draft passes all authorization business rules. */
    private boolean valid;

    /** Echoed back from the request so the client can map it to its state. */
    private AuthorizationType authorizationType;

    /**
     * Human-readable description suitable for showing in the assistant
     * confirmation dialog when valid=true.
     */
    private String message;

    /** Structured error messages for every rule that failed. */
    private List<String> errors = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Convenience factory methods
    // -----------------------------------------------------------------------

    public static ValidateAuthorizationDraftResponseDto valid(
            AuthorizationType authorizationType,
            String message) {
        ValidateAuthorizationDraftResponseDto dto = new ValidateAuthorizationDraftResponseDto();
        dto.valid = true;
        dto.authorizationType = authorizationType;
        dto.message = message;
        return dto;
    }

    public static ValidateAuthorizationDraftResponseDto invalid(
            AuthorizationType authorizationType,
            List<String> errors) {
        ValidateAuthorizationDraftResponseDto dto = new ValidateAuthorizationDraftResponseDto();
        dto.valid = false;
        dto.authorizationType = authorizationType;
        dto.errors = new ArrayList<>(errors);
        return dto;
    }

    public static ValidateAuthorizationDraftResponseDto invalid(String error) {
        ValidateAuthorizationDraftResponseDto dto = new ValidateAuthorizationDraftResponseDto();
        dto.valid = false;
        dto.errors = new ArrayList<>(List.of(error));
        return dto;
    }

    // -----------------------------------------------------------------------
    // Getters and setters
    // -----------------------------------------------------------------------

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public AuthorizationType getAuthorizationType() {
        return authorizationType;
    }

    public void setAuthorizationType(AuthorizationType authorizationType) {
        this.authorizationType = authorizationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }
}
