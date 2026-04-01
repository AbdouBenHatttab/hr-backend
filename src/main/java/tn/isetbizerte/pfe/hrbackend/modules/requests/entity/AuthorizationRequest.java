package tn.isetbizerte.pfe.hrbackend.modules.requests.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "authorization_requests")
public class AuthorizationRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthorizationType authorizationType;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    private String hrNote;

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    public AuthorizationRequest() { this.requestedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public AuthorizationType getAuthorizationType() { return authorizationType; }
    public void setAuthorizationType(AuthorizationType t) { this.authorizationType = t; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getHrNote() { return hrNote; }
    public void setHrNote(String hrNote) { this.hrNote = hrNote; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String t) { this.verificationToken = t; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime t) { this.requestedAt = t; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime t) { this.processedAt = t; }

    public String getEmployeeFullName() {
        if (user != null && user.getPerson() != null)
            return user.getPerson().getFirstName() + " " + user.getPerson().getLastName();
        return "Unknown";
    }
    public String getEmployeeEmail() {
        if (user != null && user.getPerson() != null) return user.getPerson().getEmail();
        return "";
    }
    public String getEmployeeDepartment() {
        if (user != null && user.getPerson() != null && user.getPerson().getDepartment() != null)
            return user.getPerson().getDepartment();
        return "";
    }
}
