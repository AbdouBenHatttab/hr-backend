package tn.isetbizerte.pfe.hrbackend.modules.team.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @OneToOne
    @JoinColumn(name = "leader_id", referencedColumnName = "id")
    private User teamLeader;

    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    private List<User> members = new ArrayList<>();

    // Max % of team that can be on leave simultaneously (default 40%)
    @Column(nullable = false)
    private int maxLeavePercentage = 40;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Team() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getTeamLeader() { return teamLeader; }
    public void setTeamLeader(User teamLeader) { this.teamLeader = teamLeader; }

    public List<User> getMembers() { return members; }
    public void setMembers(List<User> members) { this.members = members; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getMaxLeavePercentage() { return maxLeavePercentage; }
    public void setMaxLeavePercentage(int maxLeavePercentage) { this.maxLeavePercentage = maxLeavePercentage; }
}
