package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

public class HrDecisionNoteDto {

    private String hrNote;

    public String getHrNote() {
        return hrNote;
    }

    public void setHrNote(String hrNote) {
        this.hrNote = hrNote;
    }

    public String resolveHrNote() {
        return hrNote != null ? hrNote : "";
    }
}
