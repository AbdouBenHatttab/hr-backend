package tn.isetbizerte.pfe.hrbackend.modules.assistant.dto;

/**
 * SafeAssistantContext
 * --------------------
 * Typed, role-scoped context forwarded from Spring Boot to the FastAPI AI service.
 *
 * HARD SAFETY RULES — the following are NEVER present anywhere in this record:
 *   keycloakId, JWT / token values, password, salary, currentMonthlyDeductions,
 *   phone, address, birthDate, maritalStatus, numberOfChildren, avatarPhoto, email,
 *   raw User / Person / Team entities, team member lists, login history,
 *   document file storage paths.
 *
 * Nullability contract:
 *   - displayName   : null when Person is absent or both name parts are missing.
 *   - employee      : non-null for EMPLOYEE and TEAM_LEADER; null for HR_MANAGER and NEW_USER.
 *   - team          : non-null for TEAM_LEADER (may still carry null sub-fields if no team
 *                     is assigned yet); null for all other roles.
 *   - hr            : non-null for HR_MANAGER; null for all other roles.
 *   - teamLeaveDecision: optional read-only Team Leader decision-support context for one
 *                     selected leave request; absent when not requested or not authorized.
 *
 * Every numeric field within the nested records is a primitive long or int (never boxed
 * when a safe default of 0 is appropriate) so that serialization never produces
 * unexpected nulls for counts.
 */
public record SafeAssistantContext(

        /** First + last name only. Never email, phone, or any identifier. */
        String displayName,

        /**
         * Personal-employee context.
         * Present for EMPLOYEE and TEAM_LEADER; absent for HR_MANAGER and NEW_USER.
         */
        EmployeeContext employee,

        /**
         * Safe team summary for Team Leader.
         * Present for TEAM_LEADER only; absent for all other roles.
         * Sub-fields may be null if the Team Leader has no team assigned yet.
         */
        TeamContext team,

        /**
         * HR management context.
         * Present for HR_MANAGER only; absent for all other roles.
         */
        HrContext hr,

        /**
         * Read-only selected leave request context for Team Leader decision support.
         * Present only when a TEAM_LEADER asks with an authorized selectedLeaveRequestId.
         */
        TeamLeaveDecisionContext teamLeaveDecision

) {

    // -------------------------------------------------------------------------
    // Nested records
    // -------------------------------------------------------------------------

    /**
     * Safe employee-level context.
     * Contains only the authenticated user's own data — never another employee's data.
     *
     * annualAvailableDays : integer days remaining in the ANNUAL leave balance for the
     *                       current year; null when the balance has not been initialised yet.
     * sickAvailableDays   : integer days remaining in the SICK leave balance;
     *                       null when the balance has not been initialised yet.
     * totalPendingRequests : sum of all open requests across all request types.
     * leavesPending        : open leave requests awaiting any approval.
     * documentsPending     : document requests submitted but not yet fulfilled (PENDING status).
     *                        Does NOT include approved requests awaiting file upload.
     * documentsAwaitingFile: approved document requests for which HR has not yet uploaded
     *                        the file. Exposed separately so the AI assistant can explain
     *                        each state precisely. Counted in totalPendingRequests.
     * loansPending         : open loan requests (submitted or awaiting file upload — merged
     *                        because both states are indistinguishable to the employee).
     * authorizationsPending: open authorization requests.
     */
    public record EmployeeContext(
            Integer annualAvailableDays,
            Integer sickAvailableDays,
            long totalPendingRequests,
            long leavesPending,
            long documentsPending,
            long documentsAwaitingFile,
            long loansPending,
            long authorizationsPending
    ) {}

    /**
     * Safe team summary for Team Leader.
     *
     * teamName                   : display name of the team; null if no team assigned.
     * memberCount                : number of regular members in the team (excludes leader).
     * pendingTeamLeaderApprovals : number of leave requests in the team that are still
     *                              awaiting the Team Leader's decision.
     */
    public record TeamContext(
            String teamName,
            Integer memberCount,
            long pendingTeamLeaderApprovals
    ) {}

    /**
     * Safe HR management context.
     * Contains only platform-wide aggregate counts — never individual employee data.
     *
     * totalPendingActions      : sum of all platform-level pending items.
     * leavesPending            : leave requests awaiting final HR decision.
     * documentsPending         : document requests submitted but awaiting HR action (PENDING status).
     *                           Does NOT include approved requests awaiting file upload.
     * documentsAwaitingFile    : approved document requests for which HR has not yet uploaded
     *                           the file. Exposed separately so the AI assistant can explain
     *                           each state precisely. Counted in totalPendingActions.
     * loansPending             : loan requests pending HR action (submitted or awaiting file
     *                           upload — merged because both states are indistinguishable to HR).
     * authorizationsPending    : authorization requests pending HR action.
     * newUsersPendingApproval  : users with NEW_USER role waiting for HR onboarding.
     */
    public record HrContext(
            long totalPendingActions,
            long leavesPending,
            long documentsPending,
            long documentsAwaitingFile,
            long loansPending,
            long authorizationsPending,
            long newUsersPendingApproval
    ) {}

    /**
     * Safe selected leave request context for Team Leader decision support.
     * Contains only data already visible to the Team Leader and aggregate counts.
     * Never includes email, keycloakId, JWT, salary, private profile data, raw entities,
     * or systemRecommendation approve/reject wording.
     */
    public record TeamLeaveDecisionContext(
            boolean available,
            String unavailableReason,
            Long leaveRequestId,
            String employeeDisplayName,
            String leaveType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            Integer deductedWorkingDays,
            String status,
            String approvalStage,
            String reason,
            long overlappingApprovedLeaves,
            long overlappingPendingLeaves,
            Integer teamMemberCount,
            long activeTaskCount,
            long dueSoonTaskCount,
            long overdueTaskCount,
            long highPriorityTaskCount,
            boolean workloadContextAvailable,
            boolean overlapContextAvailable
    ) {}
}
