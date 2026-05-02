package tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.event.RequestEvent;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.inbox.ProcessedEventService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
public class RequestEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventProducer notificationEventProducer;
    private final ProcessedEventService processedEventService;
    private final UserRepository userRepository;
    private final HREmailService emailService;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final StoredEmployeeDocumentRepository storedEmployeeDocumentRepository;

    public RequestEventConsumer(ObjectMapper objectMapper,
                                NotificationEventProducer notificationEventProducer,
                                ProcessedEventService processedEventService,
                                UserRepository userRepository,
                                HREmailService emailService,
                                AuthorizationRequestRepository authorizationRequestRepository,
                                LoanRequestRepository loanRequestRepository,
                                StoredEmployeeDocumentRepository storedEmployeeDocumentRepository) {
        this.objectMapper = objectMapper;
        this.notificationEventProducer = notificationEventProducer;
        this.processedEventService = processedEventService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
        this.storedEmployeeDocumentRepository = storedEmployeeDocumentRepository;
    }

    @KafkaListener(topics = "${app.kafka.topic.request-events}")
    public void handleRequestEvent(String payload) {
        try {
            RequestEvent event = objectMapper.readValue(payload, RequestEvent.class);
            log.info("Request event received: eventId={} eventType={} requestType={} requestId={} employeeId={}",
                    event.getEventId(), event.getType(), event.getRequestType(), event.getRequestId(), event.getEmployeeId());
            String requestDedupKey = resolveRequestDedupKey(event);
            if (processedEventService.isProcessed(requestDedupKey)) {
                log.warn("Skipping duplicate request event: {}", requestDedupKey);
                return;
            }

            // Notify the employee (request owner)
            NotificationEvent employeeNotification = new NotificationEvent(
                    event.getEmployeeId(),
                    buildEmployeeMessage(event),
                    event.getType(),
                    event.getRequestType(),
                    event.getRequestId(),
                    resolveEmployeeActionUrl(event)
            );
            employeeNotification.setEventId(requestDedupKey + ":employee-notification");
            notificationEventProducer.publish(employeeNotification);
            log.info("Notification event published for request event: notificationEventId={} eventType={} requestType={} requestId={} employeeId={}",
                    employeeNotification.getEventId(), event.getType(), event.getRequestType(), event.getRequestId(), event.getEmployeeId());

            // Notify the Team Leader for team-scoped visibility.
            maybeNotifyLeader(event, requestDedupKey);

            log.info("Checking non-leave document email branch: requestId={} eventType={}",
                    event.getRequestId(), event.getType());
            maybeSendDocumentReadyEmail(event);

            log.info("Checking required document email branch: requestId={} eventType={}",
                    event.getRequestId(), event.getType());
            if (!maybeSendRequiredDocumentEmail(event)) {
                throw new IllegalStateException("Required document email was not sent for storedDocumentId=" + event.getRequestId());
            }

            log.info("Checking non-leave loan email branch: requestId={} eventType={}",
                    event.getRequestId(), event.getType());
            if (!maybeSendLoanDecisionEmail(event)) {
                throw new IllegalStateException("Loan decision email was not sent for requestId=" + event.getRequestId());
            }
            maybeSendLoanMeetingEmail(event);
            maybeSendLoanFinalFileReadyEmail(event);

            log.info("Checking non-leave authorization email branch: requestId={} eventType={}",
                    event.getRequestId(), event.getType());
            if (!maybeSendAuthorizationDecisionEmail(event)) {
                throw new IllegalStateException("Authorization decision email was not sent for requestId=" + event.getRequestId());
            }
            processedEventService.tryMarkProcessed(requestDedupKey, "request-events");
        } catch (Exception e) {
            log.error("Failed to process request event payload", e);
            throw new IllegalStateException("Request event processing failed", e);
        }
    }

    private void maybeNotifyLeader(RequestEvent event, String requestDedupKey) {
        try {
            doMaybeNotifyLeader(event, requestDedupKey);
        } catch (Exception e) {
            log.error("Failed to publish team leader notification for document request event; continuing request event processing. eventType={} requestId={} employeeId={} message={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), e.getMessage(), e);
        }
    }

    private void doMaybeNotifyLeader(RequestEvent event, String requestDedupKey) {
        if (event.getRequestType() == null) return;
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) {
            log.warn("Skipping team leader notification: missing employeeId eventType={} requestId={} requestType={}",
                    event.getType(), event.getRequestId());
            return;
        }

        User employee = userRepository.findByKeycloakIdWithPersonAndTeamLeader(event.getEmployeeId()).orElse(null);
        if (employee == null) {
            log.warn("Skipping team leader notification: employee not found eventType={} requestId={} employeeId={} requestType={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), event.getRequestType());
            return;
        }
        if (employee.getTeam() == null) {
            log.warn("Skipping team leader notification: employee has no team eventType={} requestId={} employeeId={} requestType={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), event.getRequestType());
            return;
        }
        if (employee.getTeam().getTeamLeader() == null) {
            log.warn("Skipping team leader notification: team has no leader eventType={} requestId={} employeeId={} requestType={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), event.getRequestType());
            return;
        }

        User leader = employee.getTeam().getTeamLeader();
        if (leader.getKeycloakId() == null || leader.getKeycloakId().isBlank()) {
            log.warn("Skipping team leader notification: leader has no keycloakId eventType={} requestId={} employeeId={} requestType={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), event.getRequestType());
            return;
        }
        if (leader.getKeycloakId().equals(employee.getKeycloakId())) {
            log.warn("Skipping team leader notification: employee is own team leader eventType={} requestId={} employeeId={} requestType={}",
                    event.getType(), event.getRequestId(), event.getEmployeeId(), event.getRequestType());
            return;
        }

        String type = event.getType() == null ? "" : event.getType();
        String employeeName = employee.getPerson() != null && employee.getPerson().getFirstName() != null
                ? (employee.getPerson().getFirstName() + " " + (employee.getPerson().getLastName() == null ? "" : employee.getPerson().getLastName())).trim()
                : employee.getUsername();

        String normalizedRequestType = event.getRequestType().toUpperCase();
        String message;
        String notificationType;
        String actionUrl;
        if ("LEAVE".equals(normalizedRequestType) && "LEAVE_SUBMITTED".equals(type)) {
            message = employeeName + " submitted a leave request awaiting your review.";
            notificationType = "TEAM_LEAVE_SUBMITTED";
            actionUrl = "/team/requests";
        } else if ("DOCUMENT".equals(normalizedRequestType) && type.startsWith("DOCUMENT_")) {
            message = switch (type) {
                case "DOCUMENT_SUBMITTED" -> employeeName + " submitted a document request.";
                case "DOCUMENT_APPROVED" -> "Document request approved for " + employeeName + ".";
                case "DOCUMENT_READY", "DOCUMENT_FINAL_FILE_READY" -> "Document file ready for " + employeeName + ".";
                case "DOCUMENT_REJECTED" -> "Document request rejected for " + employeeName + ".";
                default -> null;
            };
            notificationType = "TEAM_" + type;
            actionUrl = "/team/dashboard";
        } else {
            return;
        }
        if (message == null) return;

        NotificationEvent leaderNotification = new NotificationEvent(
                leader.getKeycloakId(),
                message,
                notificationType,
                normalizedRequestType,
                event.getRequestId(),
                actionUrl
        );
        leaderNotification.setEventId(requestDedupKey + ":leader-notification");
        notificationEventProducer.publish(leaderNotification);
        log.info("Team leader notification published: notificationEventId={} eventType={} requestType={} requestId={} employeeId={} leaderId={}",
                leaderNotification.getEventId(), event.getType(), normalizedRequestType, event.getRequestId(), event.getEmployeeId(), leader.getKeycloakId());
    }

    private String buildEmployeeMessage(RequestEvent event) {
        String type = event.getType();
        if (type == null) return "Request updated.";
        return switch (type) {
            case "LEAVE_SUBMITTED" -> "Your leave request was submitted.";
            case "LEAVE_APPROVED" -> "Your leave request was approved.";
            case "LEAVE_REJECTED" -> "Your leave request was rejected.";
            case "LOAN_SUBMITTED" -> "Your loan request was submitted.";
            case "LOAN_SYSTEM_REJECTED" -> "Your loan request was rejected automatically based on eligibility rules.";
            case "LOAN_CANCELLED_BY_EMPLOYEE" -> "Your loan request was cancelled.";
            case "LOAN_MEETING_SCHEDULED" -> "Your loan meeting has been scheduled.";
            case "LOAN_MEETING_UPDATED" -> "Your loan meeting has been updated.";
            case "LOAN_APPROVED" -> "Your loan request was approved.";
            case "LOAN_REJECTED" -> "Your loan request was rejected.";
            case "LOAN_CANCELLED_AFTER_MEETING" -> "Your loan request was canceled after the meeting.";
            case "LOAN_FINAL_FILE_READY" -> "Your final HR loan document is ready.";
            case "DOCUMENT_SUBMITTED" -> "Your document request was submitted.";
            case "DOCUMENT_APPROVED" -> "Your document request was approved. HR will upload the final file when it is ready.";
            case "DOCUMENT_READY", "DOCUMENT_FINAL_FILE_READY" -> "Your document is ready for download.";
            case "DOCUMENT_REJECTED" -> "Your document request was rejected.";
            case "REQUIRED_DOCUMENT_UPLOADED" -> "Your contract copy has been added to your required HR documents.";
            case "REQUIRED_DOCUMENT_REPLACED" -> "Your contract copy has been updated in your required HR documents.";
            case "AUTH_SUBMITTED" -> "Your authorization request was submitted.";
            case "AUTH_APPROVED" -> "Your authorization request was approved.";
            case "AUTH_REJECTED" -> "Your authorization request was rejected.";
            default -> "Request updated.";
        };
    }

    private String resolveEmployeeActionUrl(RequestEvent event) {
        String requestType = event.getRequestType();
        if (requestType == null) return null;
        return switch (requestType.toUpperCase()) {
            case "LEAVE" -> "/employee/leave";
            case "LOAN" -> "/employee/loans";
            case "DOCUMENT", "REQUIRED_DOCUMENT" -> "/employee/documents";
            case "AUTH" -> "/employee/authorizations";
            default -> null;
        };
    }

    private boolean maybeSendRequiredDocumentEmail(RequestEvent event) {
        if (!"REQUIRED_DOCUMENT_UPLOADED".equals(event.getType())
                && !"REQUIRED_DOCUMENT_REPLACED".equals(event.getType())) {
            return true;
        }
        log.info("Entering required document email branch: eventType={} storedDocumentId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "required document");
        requireRequestIdForEmail(event, "required document");

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped required document email: dedupKey={} storedDocumentId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return true;
        }

        StoredEmployeeDocument document = storedEmployeeDocumentRepository.findById(event.getRequestId())
                .orElseThrow(() -> emailFailure(event, "required document", "StoredEmployeeDocument not found"));
        User employee = resolveEmployeeForEmail(event, "required document");
        var person = employee.getPerson();
        boolean replaced = "REQUIRED_DOCUMENT_REPLACED".equals(event.getType());
        String referenceId = "REQ-DOC-" + String.format("%06d", event.getRequestId());

        log.info("Calling HREmailService method for required document email: method=sendRequiredDocumentUploaded storedDocumentId={} recipientEmail={} replaced={} eventType={}",
                event.getRequestId(), person.getEmail(), replaced, event.getType());
        boolean sent = emailService.sendRequiredDocumentUploaded(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                document.getDocumentType() != null ? formatLabel(document.getDocumentType().name()) : "Contract Copy",
                document.getFileName(),
                replaced,
                referenceId
        );
        log.info("Email send result for required document email: storedDocumentId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "required-document-email");
        } else {
            throw emailFailure(event, "required document", "HREmailService.sendRequiredDocumentUploaded returned false");
        }
        return true;
    }

    private void maybeSendDocumentReadyEmail(RequestEvent event) {
        if (!"DOCUMENT_READY".equals(event.getType()) && !"DOCUMENT_FINAL_FILE_READY".equals(event.getType())) return;
        log.info("Entering document email branch: eventType={} requestId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "document");
        requireRequestIdForEmail(event, "document");

        String emailDedupKey = "email:DOCUMENT_READY:" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped document email: dedupKey={} requestId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return;
        }

        User employee = resolveEmployeeForEmail(event, "document");
        var person = employee.getPerson();

        log.info("Calling HREmailService method for document email: method=sendDocumentReady requestId={} recipientEmail={} eventType={}",
                event.getRequestId(), person.getEmail(), event.getType());
        boolean sent = emailService.sendDocumentReady(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                "DOC-" + String.format("%06d", event.getRequestId())
        );
        log.info("Email send result for document email: method=sendDocumentReady requestId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (!sent) {
            throw emailFailure(event, "document", "HREmailService.sendDocumentReady returned false");
        }
        processedEventService.tryMarkProcessed(emailDedupKey, "document-ready-email");
    }

    private boolean maybeSendAuthorizationDecisionEmail(RequestEvent event) {
        if (!"AUTH_APPROVED".equals(event.getType()) && !"AUTH_REJECTED".equals(event.getType())) return true;
        log.info("Entering authorization email branch: eventType={} requestId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "authorization");
        requireRequestIdForEmail(event, "authorization");

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped authorization email: dedupKey={} requestId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return true;
        }

        AuthorizationRequest request = authorizationRequestRepository.findById(event.getRequestId())
                .orElseThrow(() -> emailFailure(event, "authorization", "AuthorizationRequest not found"));

        User employee = resolveEmployeeForEmail(event, "authorization");
        var person = employee.getPerson();

        boolean approved = "AUTH_APPROVED".equals(event.getType());
        log.info("Calling HREmailService method for authorization email: method=sendAuthorizationDecision requestId={} recipientEmail={} approved={} eventType={}",
                event.getRequestId(), person.getEmail(), approved, event.getType());
        boolean sent = emailService.sendAuthorizationDecision(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null,
                request.getStartDate(),
                request.getEndDate(),
                request.getAbsenceDate(),
                request.getFromTime(),
                request.getToTime(),
                request.getEquipmentType(),
                request.getProcessedAt(),
                approved,
                request.getHrNote(),
                "AUTH-" + String.format("%06d", event.getRequestId())
        );
        log.info("Email send result for authorization email: method=sendAuthorizationDecision requestId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "authorization-decision-email");
        } else {
            throw emailFailure(event, "authorization", "HREmailService.sendAuthorizationDecision returned false");
        }
        return true;
    }

    private boolean maybeSendLoanDecisionEmail(RequestEvent event) {
        if (!"LOAN_APPROVED".equals(event.getType())
                && !"LOAN_REJECTED".equals(event.getType())
                && !"LOAN_CANCELLED_AFTER_MEETING".equals(event.getType())
                && !"LOAN_SYSTEM_REJECTED".equals(event.getType())) {
            return true;
        }
        log.info("Entering loan email branch: eventType={} requestId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "loan");
        requireRequestIdForEmail(event, "loan");

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped loan email: dedupKey={} requestId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return true;
        }

        LoanRequest request = loanRequestRepository.findById(event.getRequestId())
                .orElseThrow(() -> emailFailure(event, "loan", "LoanRequest not found"));

        User employee = resolveEmployeeForEmail(event, "loan");
        var person = employee.getPerson();

        String refId = "LOAN-" + String.format("%06d", event.getRequestId());
        boolean approved = "LOAN_APPROVED".equals(event.getType());
        double amount = approved && request.getApprovedAmount() != null
                ? request.getApprovedAmount().doubleValue()
                : request.getAmount() != null ? request.getAmount().doubleValue() : 0;
        boolean sent;
        if (approved) {
            double installment = request.getMonthlyInstallment() != null
                    ? request.getMonthlyInstallment().doubleValue()
                    : 0;
            log.info("Calling HREmailService method for loan email: method=sendLoanApproved requestId={} recipientEmail={} eventType={}",
                    event.getRequestId(), person.getEmail(), event.getType());
            sent = emailService.sendLoanApproved(
                    person.getEmail(),
                    person.getFirstName(),
                    person.getLastName(),
                    amount,
                    request.getRepaymentMonths() != null ? request.getRepaymentMonths() : 0,
                    installment,
                    refId
            );
        } else {
            String reason = firstNonBlank(
                    request.getHrDecisionReason(),
                    request.getCancellationReason(),
                    request.getHrNote(),
                    event.getComment(),
                    request.getDecisionReason()
            );
            log.info("Calling HREmailService method for loan email: method=sendLoanRejected requestId={} recipientEmail={} eventType={} reasonPresent={}",
                    event.getRequestId(), person.getEmail(), event.getType(), reason != null && !reason.isBlank());
            sent = emailService.sendLoanRejected(
                    person.getEmail(),
                    person.getFirstName(),
                    person.getLastName(),
                    amount,
                    reason,
                    refId
            );
        }
        log.info("Email send result for loan email: requestId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "loan-decision-email");
        } else {
            String method = approved ? "HREmailService.sendLoanApproved" : "HREmailService.sendLoanRejected";
            throw emailFailure(event, "loan", method + " returned false");
        }
        return true;
    }

    private void maybeSendLoanMeetingEmail(RequestEvent event) {
        if (!"LOAN_MEETING_SCHEDULED".equals(event.getType())
                && !"LOAN_MEETING_UPDATED".equals(event.getType())) {
            return;
        }
        log.info("Entering loan meeting email branch: eventType={} requestId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "loan meeting");
        requireRequestIdForEmail(event, "loan meeting");

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped loan meeting email: dedupKey={} requestId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return;
        }

        LoanRequest request = loanRequestRepository.findById(event.getRequestId())
                .orElseThrow(() -> emailFailure(event, "loan meeting", "LoanRequest not found"));
        User employee = resolveEmployeeForEmail(event, "loan meeting");
        var person = employee.getPerson();
        boolean updated = "LOAN_MEETING_UPDATED".equals(event.getType());
        String refId = "LOAN-" + String.format("%06d", event.getRequestId());

        log.info("Calling HREmailService method for loan meeting email: method=sendLoanMeetingNotification requestId={} recipientEmail={} updated={}",
                event.getRequestId(), person.getEmail(), updated);
        boolean sent = emailService.sendLoanMeetingNotification(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                request.getMeetingAt(),
                updated,
                refId
        );
        log.info("Email send result for loan meeting email: requestId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "loan-meeting-email");
        } else {
            throw emailFailure(event, "loan meeting", "HREmailService.sendLoanMeetingNotification returned false");
        }
    }

    private void maybeSendLoanFinalFileReadyEmail(RequestEvent event) {
        if (!"LOAN_FINAL_FILE_READY".equals(event.getType())) {
            return;
        }
        log.info("Entering loan final file email branch: eventType={} requestId={} employeeId={}",
                event.getType(), event.getRequestId(), event.getEmployeeId());
        requireEmployeeIdForEmail(event, "loan final file");
        requireRequestIdForEmail(event, "loan final file");

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Dedupe skipped loan final file email: dedupKey={} requestId={} eventType={}",
                    emailDedupKey, event.getRequestId(), event.getType());
            return;
        }

        User employee = resolveEmployeeForEmail(event, "loan final file");
        var person = employee.getPerson();
        String refId = "LOAN-" + String.format("%06d", event.getRequestId());

        log.info("Calling HREmailService method for loan final file email: method=sendLoanFinalFileReady requestId={} recipientEmail={}",
                event.getRequestId(), person.getEmail());
        boolean sent = emailService.sendLoanFinalFileReady(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                refId
        );
        log.info("Email send result for loan final file email: requestId={} eventType={} recipientEmail={} result={}",
                event.getRequestId(), event.getType(), person.getEmail(), sent);
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "loan-final-file-email");
        } else {
            throw emailFailure(event, "loan final file", "HREmailService.sendLoanFinalFileReady returned false");
        }
    }

    private void requireEmployeeIdForEmail(RequestEvent event, String emailFlow) {
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) {
            throw emailFailure(event, emailFlow, "missing employeeId");
        }
    }

    private void requireRequestIdForEmail(RequestEvent event, String emailFlow) {
        if (event.getRequestId() == null) {
            throw emailFailure(event, emailFlow, "missing requestId");
        }
    }

    private User resolveEmployeeForEmail(RequestEvent event, String emailFlow) {
        User employee = userRepository.findByKeycloakId(event.getEmployeeId())
                .orElseThrow(() -> emailFailure(event, emailFlow, "User not found for employeeId"));
        if (employee.getPerson() == null) {
            throw emailFailure(event, emailFlow, "User has no person profile");
        }
        if (employee.getPerson().getEmail() == null || employee.getPerson().getEmail().isBlank()) {
            throw emailFailure(event, emailFlow, "Person email is missing");
        }
        log.info("Resolved recipient email for {} email: eventType={} requestId={} employeeId={} recipientEmail={}",
                emailFlow, event.getType(), event.getRequestId(), event.getEmployeeId(), employee.getPerson().getEmail());
        return employee;
    }

    private IllegalStateException emailFailure(RequestEvent event, String emailFlow, String reason) {
        log.error("Cannot send {} email: reason={} eventType={} requestId={} employeeId={}",
                emailFlow, reason, event.getType(), event.getRequestId(), event.getEmployeeId());
        return new IllegalStateException(emailFlow + " email cannot be sent: " + reason
                + " eventType=" + event.getType()
                + " requestId=" + event.getRequestId()
                + " employeeId=" + event.getEmployeeId());
    }

    private String resolveRequestDedupKey(RequestEvent event) {
        if (event.getRequestId() != null && event.getType() != null && !event.getType().isBlank()) {
            return "request:" + UUID.nameUUIDFromBytes((
                    value(event.getType()) + "|" +
                    value(event.getRequestType()) + "|" +
                    value(event.getRequestId()) + "|" +
                    value(event.getEmployeeId())
            ).getBytes(StandardCharsets.UTF_8));
        }
        if (event.getEventId() != null && !event.getEventId().isBlank()) {
            return event.getEventId();
        }
        String signature = value(event.getType()) + "|" +
                value(event.getRequestType()) + "|" +
                value(event.getEmployeeId()) + "|" +
                value(event.getActorId()) + "|" +
                value(event.getTimestamp());
        return "request:" + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String formatLabel(String enumValue) {
        if (enumValue == null || enumValue.isBlank()) return "";
        String[] parts = enumValue.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
