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
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
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

    public RequestEventConsumer(ObjectMapper objectMapper,
                                NotificationEventProducer notificationEventProducer,
                                ProcessedEventService processedEventService,
                                UserRepository userRepository,
                                HREmailService emailService,
                                AuthorizationRequestRepository authorizationRequestRepository,
                                LoanRequestRepository loanRequestRepository) {
        this.objectMapper = objectMapper;
        this.notificationEventProducer = notificationEventProducer;
        this.processedEventService = processedEventService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.loanRequestRepository = loanRequestRepository;
    }

    @KafkaListener(topics = "${app.kafka.topic.request-events}")
    public void handleRequestEvent(String payload) {
        try {
            RequestEvent event = objectMapper.readValue(payload, RequestEvent.class);
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

            // Notify the Team Leader for team-scoped visibility (currently: document requests)
            maybeNotifyLeader(event, requestDedupKey);
            maybeSendDocumentReadyEmail(event);
            if (!maybeSendLoanDecisionEmail(event)) {
                throw new IllegalStateException("Loan decision email was not sent for requestId=" + event.getRequestId());
            }
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
        if (event.getRequestType() == null || !"DOCUMENT".equalsIgnoreCase(event.getRequestType())) return;
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) return;

        User employee = userRepository.findByKeycloakId(event.getEmployeeId()).orElse(null);
        if (employee == null || employee.getTeam() == null || employee.getTeam().getTeamLeader() == null) return;

        User leader = employee.getTeam().getTeamLeader();
        if (leader.getKeycloakId() == null || leader.getKeycloakId().isBlank()) return;
        if (leader.getKeycloakId().equals(employee.getKeycloakId())) return;

        String type = event.getType() == null ? "" : event.getType();
        if (!type.startsWith("DOCUMENT_")) return;

        String employeeName = employee.getPerson() != null && employee.getPerson().getFirstName() != null
                ? (employee.getPerson().getFirstName() + " " + (employee.getPerson().getLastName() == null ? "" : employee.getPerson().getLastName())).trim()
                : employee.getUsername();

        String message = switch (type) {
            case "DOCUMENT_SUBMITTED" -> employeeName + " submitted a document request.";
            case "DOCUMENT_APPROVED" -> "Document request approved for " + employeeName + ".";
            case "DOCUMENT_READY", "DOCUMENT_FINAL_FILE_READY" -> "Document file ready for " + employeeName + ".";
            case "DOCUMENT_REJECTED" -> "Document request rejected for " + employeeName + ".";
            default -> null;
        };
        if (message == null) return;

        NotificationEvent leaderNotification = new NotificationEvent(
                leader.getKeycloakId(),
                message,
                "TEAM_" + type,
                "DOCUMENT",
                event.getRequestId(),
                "/team/dashboard"
        );
        leaderNotification.setEventId(requestDedupKey + ":leader-notification");
        notificationEventProducer.publish(leaderNotification);
    }

    private String buildEmployeeMessage(RequestEvent event) {
        String type = event.getType();
        if (type == null) return "Request updated.";
        return switch (type) {
            case "LEAVE_SUBMITTED" -> "Your leave request was submitted.";
            case "LEAVE_APPROVED" -> "Your leave request was approved.";
            case "LEAVE_REJECTED" -> "Your leave request was rejected.";
            case "LOAN_SUBMITTED" -> "Your loan request was submitted.";
            case "LOAN_APPROVED" -> "Your loan request was approved.";
            case "LOAN_REJECTED" -> "Your loan request was rejected.";
            case "LOAN_CANCELLED_AFTER_MEETING" -> "Your loan request was canceled after the meeting.";
            case "DOCUMENT_SUBMITTED" -> "Your document request was submitted.";
            case "DOCUMENT_APPROVED" -> "Your document request was approved. HR will upload the final file when it is ready.";
            case "DOCUMENT_READY", "DOCUMENT_FINAL_FILE_READY" -> "Your document is ready for download.";
            case "DOCUMENT_REJECTED" -> "Your document request was rejected.";
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
            case "DOCUMENT" -> "/employee/documents";
            case "AUTH" -> "/employee/authorizations";
            default -> null;
        };
    }

    private void maybeSendDocumentReadyEmail(RequestEvent event) {
        if (!"DOCUMENT_READY".equals(event.getType()) && !"DOCUMENT_FINAL_FILE_READY".equals(event.getType())) return;
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) return;
        if (event.getRequestId() == null) return;

        String emailDedupKey = "email:DOCUMENT_READY:" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Skipping duplicate document ready email for requestId={}", event.getRequestId());
            return;
        }

        User employee = userRepository.findByKeycloakId(event.getEmployeeId()).orElse(null);
        if (employee == null || employee.getPerson() == null) return;
        var person = employee.getPerson();
        if (person.getEmail() == null || person.getEmail().isBlank()) return;

        boolean sent = emailService.sendDocumentReady(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                "DOC-" + String.format("%06d", event.getRequestId())
        );
        if (!sent) {
            throw new IllegalStateException("Document ready email was not sent for requestId=" + event.getRequestId());
        }
        processedEventService.tryMarkProcessed(emailDedupKey, "document-ready-email");
    }

    private boolean maybeSendAuthorizationDecisionEmail(RequestEvent event) {
        if (!"AUTH_APPROVED".equals(event.getType()) && !"AUTH_REJECTED".equals(event.getType())) return true;
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) return true;
        if (event.getRequestId() == null) return true;

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Skipping duplicate authorization decision email for requestId={}", event.getRequestId());
            return true;
        }

        AuthorizationRequest request = authorizationRequestRepository.findById(event.getRequestId()).orElse(null);
        if (request == null) return false;

        User employee = userRepository.findByKeycloakId(event.getEmployeeId()).orElse(null);
        if (employee == null || employee.getPerson() == null) return false;
        var person = employee.getPerson();
        if (person.getEmail() == null || person.getEmail().isBlank()) return false;

        boolean approved = "AUTH_APPROVED".equals(event.getType());
        boolean sent = emailService.sendAuthorizationDecision(
                person.getEmail(),
                person.getFirstName(),
                person.getLastName(),
                request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null,
                request.getStartDate(),
                request.getEndDate(),
                request.getProcessedAt(),
                approved,
                request.getHrNote(),
                "AUTH-" + String.format("%06d", event.getRequestId())
        );
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "authorization-decision-email");
        } else {
            log.error("Authorization decision email was not sent. requestId={} eventType={} employeeId={}",
                    event.getRequestId(), event.getType(), event.getEmployeeId());
        }
        return sent;
    }

    private boolean maybeSendLoanDecisionEmail(RequestEvent event) {
        if (!"LOAN_APPROVED".equals(event.getType())
                && !"LOAN_REJECTED".equals(event.getType())
                && !"LOAN_CANCELLED_AFTER_MEETING".equals(event.getType())) {
            return true;
        }
        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) return true;
        if (event.getRequestId() == null) return true;

        String emailDedupKey = "email:" + event.getType() + ":" + event.getRequestId();
        if (processedEventService.isProcessed(emailDedupKey)) {
            log.warn("Skipping duplicate loan decision email for requestId={} eventType={}",
                    event.getRequestId(), event.getType());
            return true;
        }

        LoanRequest request = loanRequestRepository.findById(event.getRequestId()).orElse(null);
        if (request == null) return false;

        User employee = userRepository.findByKeycloakId(event.getEmployeeId()).orElse(null);
        if (employee == null || employee.getPerson() == null) return false;
        var person = employee.getPerson();
        if (person.getEmail() == null || person.getEmail().isBlank()) return false;

        String refId = "LOAN-" + String.format("%06d", event.getRequestId());
        double amount = request.getAmount() != null ? request.getAmount().doubleValue() : 0;
        boolean approved = "LOAN_APPROVED".equals(event.getType());
        boolean sent;
        if (approved) {
            double installment = request.getMonthlyInstallment() != null
                    ? request.getMonthlyInstallment().doubleValue()
                    : 0;
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
            sent = emailService.sendLoanRejected(
                    person.getEmail(),
                    person.getFirstName(),
                    person.getLastName(),
                    amount,
                    reason,
                    refId
            );
        }
        if (sent) {
            processedEventService.tryMarkProcessed(emailDedupKey, "loan-decision-email");
        } else {
            log.error("Loan decision email was not sent. requestId={} eventType={} employeeId={}",
                    event.getRequestId(), event.getType(), event.getEmployeeId());
        }
        return sent;
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
}
