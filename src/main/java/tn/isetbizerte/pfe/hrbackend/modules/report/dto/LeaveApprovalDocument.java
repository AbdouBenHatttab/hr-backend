package tn.isetbizerte.pfe.hrbackend.modules.report.dto;

public class LeaveApprovalDocument {

    private final byte[] content;
    private final String fileName;

    public LeaveApprovalDocument(byte[] content, String fileName) {
        this.content = content;
        this.fileName = fileName;
    }

    public byte[] getContent() {
        return content;
    }

    public String getFileName() {
        return fileName;
    }
}

