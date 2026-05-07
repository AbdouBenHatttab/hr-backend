package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;

/**
 * Request DTO for the dry-run document draft validation endpoint.
 *
 * Intentionally does NOT use cross-field @AssertTrue validators that block
 * CONTRACT_COPY — those are business rules enforced in the service so that
 * violations are returned as structured errors (valid=false, errors=[...])
 * rather than HTTP 400 responses thrown by the framework before the service
 * layer is reached.
 *
 * Mirror of CreateDocumentRequestDto fields without the @AssertTrue guard.
 */
public class ValidateDocumentDraftRequestDto {

    @NotNull(message = "documentType is required")
    @JsonAlias("type")
    private DocumentType documentType;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    private String notes;

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
