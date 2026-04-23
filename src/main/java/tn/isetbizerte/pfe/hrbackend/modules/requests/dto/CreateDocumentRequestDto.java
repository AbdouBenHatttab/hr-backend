package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;

public class CreateDocumentRequestDto {

    @NotNull(message = "Document type is required")
    @JsonAlias("type")
    private DocumentType documentType;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    @AssertTrue(message = "Contract copy is an HR-managed required document and cannot be requested by employees")
    public boolean isRequestableDocumentType() {
        return documentType == null || documentType != DocumentType.CONTRACT_COPY;
    }

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
