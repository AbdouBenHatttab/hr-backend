package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

public class RejectLoanRequestDto {

    private String hrDecisionReason;
    private String reason;
    private String hrNote;

    public String getHrDecisionReason() {
        return hrDecisionReason;
    }

    public void setHrDecisionReason(String hrDecisionReason) {
        this.hrDecisionReason = hrDecisionReason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getHrNote() {
        return hrNote;
    }

    public void setHrNote(String hrNote) {
        this.hrNote = hrNote;
    }

    public String resolveNote() {
        if (hrDecisionReason != null) return hrDecisionReason;
        if (reason != null) return reason;
        return hrNote != null ? hrNote : "";
    }
}
