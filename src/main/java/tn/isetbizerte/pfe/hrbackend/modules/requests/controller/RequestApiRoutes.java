package tn.isetbizerte.pfe.hrbackend.modules.requests.controller;

public final class RequestApiRoutes {

    private RequestApiRoutes() {
    }

    public static final String EMPLOYEE_DOCUMENTS = "/api/employee/documents";
    public static final String EMPLOYEE_DOCUMENTS_VALIDATE_DRAFT = EMPLOYEE_DOCUMENTS + "/validate-draft";
    public static final String EMPLOYEE_DOCUMENTS_CANCEL = EMPLOYEE_DOCUMENTS + "/{id}/cancel";
    public static final String EMPLOYEE_DOCUMENTS_ATTACHMENT = EMPLOYEE_DOCUMENTS + "/{id}/attachment";
    public static final String EMPLOYEE_DOCUMENTS_MANAGED = EMPLOYEE_DOCUMENTS + "/managed";
    public static final String EMPLOYEE_DOCUMENTS_MANAGED_DOWNLOAD = EMPLOYEE_DOCUMENTS_MANAGED + "/{id}/download";

    public static final String EMPLOYEE_LOANS = "/api/employee/loans";
    public static final String EMPLOYEE_LOANS_VALIDATE_DRAFT = EMPLOYEE_LOANS + "/validate-draft";
    public static final String EMPLOYEE_LOANS_CANCEL = EMPLOYEE_LOANS + "/{id}/cancel";
    public static final String EMPLOYEE_LOANS_ELIGIBILITY = EMPLOYEE_LOANS + "/eligibility";
    public static final String EMPLOYEE_LOANS_ATTACHMENT = EMPLOYEE_LOANS + "/{id}/attachment";

    public static final String EMPLOYEE_AUTHORIZATIONS = "/api/employee/authorizations";
    public static final String EMPLOYEE_AUTHORIZATIONS_VALIDATE_DRAFT = EMPLOYEE_AUTHORIZATIONS + "/validate-draft";
    public static final String EMPLOYEE_AUTHORIZATIONS_CANCEL = EMPLOYEE_AUTHORIZATIONS + "/{id}/cancel";

    public static final String HR_DOCUMENTS = "/api/hr/documents";
    public static final String HR_DOCUMENTS_APPROVE = HR_DOCUMENTS + "/{id}/approve";
    public static final String HR_DOCUMENTS_REJECT = HR_DOCUMENTS + "/{id}/reject";
    public static final String HR_DOCUMENTS_ATTACHMENT = HR_DOCUMENTS + "/{id}/attachment";
    public static final String HR_DOCUMENTS_HISTORY = HR_DOCUMENTS + "/{id}/history";
    public static final String HR_USER_DOCUMENTS = "/api/hr/users/{userId}/documents";
    public static final String HR_USER_DOCUMENTS_LIST = "/api/hr/users/{userId}/documents";

    public static final String HR_LOANS = "/api/hr/loans";
    public static final String HR_LOANS_APPROVE = HR_LOANS + "/{id}/approve";
    public static final String HR_LOANS_REJECT = HR_LOANS + "/{id}/reject";
    public static final String HR_LOANS_SCHEDULE_MEETING = HR_LOANS + "/{id}/schedule-meeting";
    public static final String HR_LOANS_CANCEL_AFTER_MEETING = HR_LOANS + "/{id}/cancel-after-meeting";
    public static final String HR_LOANS_ATTACHMENT = HR_LOANS + "/{id}/attachment";
    public static final String HR_LOANS_HISTORY = HR_LOANS + "/{id}/history";

    public static final String HR_AUTHORIZATIONS = "/api/hr/authorizations";
    public static final String HR_AUTHORIZATIONS_APPROVE = HR_AUTHORIZATIONS + "/{id}/approve";
    public static final String HR_AUTHORIZATIONS_REJECT = HR_AUTHORIZATIONS + "/{id}/reject";
    public static final String HR_AUTHORIZATIONS_HISTORY = HR_AUTHORIZATIONS + "/{id}/history";
}
