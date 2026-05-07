package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for the dry-run document draft validation endpoint.
 *
 * Always returned with HTTP 200.
 * {@code valid=true}  — all rules passed; the draft can proceed to real submission.
 * {@code valid=false} — one or more rules failed; {@code errors} lists every violation.
 *
 * No side effects: this is a pure read result. Nothing is saved and no events
 * are published.
 *
 * fulfillmentMode values:
 *   "GENERATED" — Spring Boot generates the document automatically on approval
 *                 (currently only LEAVE_BALANCE_STATEMENT).
 *   "UPLOADED"  — HR must manually upload the prepared file after approval
 *                 (all other employee-requestable document types).
 */
public class ValidateDocumentDraftResponseDto {

    /** Whether the draft passes all document business rules. */
    private boolean valid;

    /** Echoed back from the request so the client can map it to its state. */
    private DocumentType documentType;

    /**
     * How this document type will be fulfilled once approved.
     * "GENERATED" or "UPLOADED". Null when documentType is invalid or missing.
     */
    private String fulfillmentMode;

    /**
     * Human-readable description of the fulfillment path, suitable for showing
     * in the assistant confirmation dialog.
     */
    private String message;

    /** Structured error messages for every rule that failed. */
    private List<String> errors = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Convenience factory methods
    // -----------------------------------------------------------------------

    public static ValidateDocumentDraftResponseDto valid(
            DocumentType documentType,
            String fulfillmentMode,
            String message) {
        ValidateDocumentDraftResponseDto dto = new ValidateDocumentDraftResponseDto();
        dto.valid = true;
        dto.documentType = documentType;
        dto.fulfillmentMode = fulfillmentMode;
        dto.message = message;
        return dto;
    }

    public static ValidateDocumentDraftResponseDto invalid(String error) {
        ValidateDocumentDraftResponseDto dto = new ValidateDocumentDraftResponseDto();
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

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(String fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
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
