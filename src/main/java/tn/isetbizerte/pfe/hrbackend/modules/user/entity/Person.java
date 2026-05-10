package tn.isetbizerte.pfe.hrbackend.modules.user.entity;

import jakarta.persistence.*;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import java.time.LocalDate;

/**
 * Human profile and HR-managed employment entity.
 *
 * Person owns personal/contact information and employment data such as name,
 * email, phone, address, marital/family fields, avatar, department, job title,
 * hire date, salary, and payroll deduction state. Authentication, role, active
 * state, and team membership remain on the linked {@link User}.
 */
@Entity
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "contact_email")
    private String contactEmail;

    private String phone;

    private LocalDate birthDate;

    private String address;

    private String maritalStatus;

    private int numberOfChildren;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department departmentRef;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitleRef;

    @Column(precision = 10, scale = 3)
    private java.math.BigDecimal salary;

    // Total monthly loan deductions currently active
    @Column(precision = 10, scale = 3)
    private java.math.BigDecimal currentMonthlyDeductions = java.math.BigDecimal.ZERO;

    private LocalDate hireDate;

    // Stored as base64 string — persists across devices
    @Column(columnDefinition = "TEXT")
    private String avatarPhoto;

    // Tailwind color class e.g. 'bg-violet-600'
    private String avatarColor;

    @OneToOne(mappedBy = "person", cascade = CascadeType.ALL)
    private User user;

    // Constructors
    public Person() {}

    public Person(String lastName, String firstName, String email) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.email = email;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public int getNumberOfChildren() {
        return numberOfChildren;
    }

    public void setNumberOfChildren(int numberOfChildren) {
        this.numberOfChildren = numberOfChildren;
    }

    public String getDepartment() {
        return departmentRef != null ? departmentRef.getName() : null;
    }

    public Long getDepartmentId() {
        return departmentRef != null ? departmentRef.getId() : null;
    }

    public String getDepartmentDescription() {
        return departmentRef != null ? departmentRef.getDescription() : null;
    }

    public Boolean getDepartmentActive() {
        return departmentRef != null ? departmentRef.getActive() : null;
    }

    public Department getDepartmentRef() {
        return departmentRef;
    }

    public void setDepartmentRef(Department departmentRef) {
        this.departmentRef = departmentRef;
    }

    public Long getJobTitleId() {
        return jobTitleRef != null ? jobTitleRef.getId() : null;
    }

    public String getJobTitle() {
        return jobTitleRef != null ? jobTitleRef.getName() : null;
    }

    public String getJobTitleDescription() {
        return jobTitleRef != null ? jobTitleRef.getDescription() : null;
    }

    public Boolean getJobTitleActive() {
        return jobTitleRef != null ? jobTitleRef.getActive() : null;
    }

    public JobTitle getJobTitleRef() {
        return jobTitleRef;
    }

    public void setJobTitleRef(JobTitle jobTitleRef) {
        this.jobTitleRef = jobTitleRef;
    }

    public java.math.BigDecimal getSalary() { return salary; }
    public void setSalary(java.math.BigDecimal salary) { this.salary = salary; }

    public java.math.BigDecimal getCurrentMonthlyDeductions() {
        return currentMonthlyDeductions != null ? currentMonthlyDeductions : java.math.BigDecimal.ZERO;
    }
    public void setCurrentMonthlyDeductions(java.math.BigDecimal d) { this.currentMonthlyDeductions = d; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public String getAvatarPhoto() { return avatarPhoto; }
    public void setAvatarPhoto(String avatarPhoto) { this.avatarPhoto = avatarPhoto; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
