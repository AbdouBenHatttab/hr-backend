package tn.isetbizerte.pfe.hrbackend.modules.user.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keycloakId;  // Links to Keycloak user ID

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private LocalDate registrationDate;

    private Boolean emailVerified;

    private Boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeRole role;

    @OneToOne
    @JoinColumn(name = "person_id", referencedColumnName = "id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LoginHistory> loginHistories = new ArrayList<>();

    // Constructors
    public User() {
        this.registrationDate = LocalDate.now();
        this.role = TypeRole.NEW_USER;  // Default role
        this.active = false;            // Inactive until HR assigns a real role
        this.emailVerified = false;
    }

    public User(String keycloakId, String username) {
        this();
        this.keycloakId = keycloakId;
        this.username = username;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public TypeRole getRole() {
        return role;
    }

    public void setRole(TypeRole role) {
        this.role = role;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public List<LoginHistory> getLoginHistories() {
        return loginHistories;
    }

    public void setLoginHistories(List<LoginHistory> loginHistories) {
        this.loginHistories = loginHistories;
    }

    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }

    public void addLoginHistory(LoginHistory loginHistory) {
        this.loginHistories.add(loginHistory);
        loginHistory.setUser(this);
    }
}

