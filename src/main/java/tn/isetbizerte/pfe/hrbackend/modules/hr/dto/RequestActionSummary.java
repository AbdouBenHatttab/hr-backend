package tn.isetbizerte.pfe.hrbackend.modules.hr.dto;

public record RequestActionSummary(
        long total,
        long leavesPending,
        long documentsPending,
        long documentsAwaitingFile,
        long loansPending,
        long loansAwaitingFile,
        long authorizationsPending
) {
    public static RequestActionSummary of(
            long leavesPending,
            long documentsPending,
            long documentsAwaitingFile,
            long loansPending,
            long loansAwaitingFile,
            long authorizationsPending
    ) {
        long total = leavesPending
                + documentsPending
                + documentsAwaitingFile
                + loansPending
                + loansAwaitingFile
                + authorizationsPending;
        return new RequestActionSummary(
                total,
                leavesPending,
                documentsPending,
                documentsAwaitingFile,
                loansPending,
                loansAwaitingFile,
                authorizationsPending
        );
    }
}
