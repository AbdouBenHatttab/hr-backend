package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentFulfillmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LoanType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.HrReportExportRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.DocumentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.LoanRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrReportExportServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private DocumentRequestRepository documentRequestRepository;
    @Mock
    private LoanRequestRepository loanRequestRepository;
    @Mock
    private AuthorizationRequestRepository authorizationRequestRepository;
    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HRService hrService;

    private HrReportExportService service;
    private Jwt jwt;
    private User hrManager;

    @BeforeEach
    void setUp() {
        service = new HrReportExportService(
                leaveRequestRepository,
                documentRequestRepository,
                loanRequestRepository,
                authorizationRequestRepository,
                authenticatedUserResolver,
                userRepository,
                hrService,
                Clock.fixed(LocalDateTime.of(2026, 5, 13, 10, 15).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("kc-hr")
                .claim("preferred_username", "hr.manager")
                .build();

        hrManager = new User();
        hrManager.setId(1L);
        hrManager.setUsername("hr.manager");
        hrManager.setKeycloakId("kc-hr");
        hrManager.setRole(TypeRole.HR_MANAGER);

        when(authenticatedUserResolver.require(jwt)).thenReturn(hrManager);
    }

    @Test
    void exportExcel_createsExpectedSheetsHeadersAndSafeActorLabels() throws Exception {
        when(hrService.getAllUsersWithDetails()).thenReturn(List.of(
                employeeMap(10L, "Amina", "Ben Ali", "amina@example.test", LocalDate.of(2024, 1, 10),
                        7L, "Engineering", 3L, "Core", "Developer", "EMPLOYEE"),
                employeeMap(11L, "Ilyes", "Mansouri", "ilyes@example.test", LocalDate.of(2023, 9, 20),
                        8L, "Finance", 4L, "Payroll", "Analyst", "EMPLOYEE")
        ));

        when(userRepository.findByKeycloakIdWithPersonAndTeamLeader("actor-kc-1"))
                .thenReturn(Optional.of(actorUser(70L, "Sana", "El Hadi", "sana.hr@example.test", "sana.hr", "HR_MANAGER")));
        when(userRepository.findByKeycloakIdWithPersonAndTeamLeader("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(Optional.empty());

        when(leaveRequestRepository.findAllForReportExport()).thenReturn(List.of(
                leaveRequest(100L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 5, 9, 0),
                        LocalDateTime.of(2026, 1, 8, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(101L, employeeUser(11L, "ilyes", TypeRole.EMPLOYEE, 4L, "Payroll"),
                        LeaveStatus.PENDING, LocalDateTime.of(2026, 1, 6, 9, 0),
                        null, null, null,
                        "Training", ApprovalDecision.PENDING, ApprovalDecision.PENDING, null)
        ));
        when(documentRequestRepository.findAllForReportExport()).thenReturn(List.of(
                documentRequest(200L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        RequestStatus.APPROVED, LocalDateTime.of(2026, 1, 7, 11, 0), LocalDateTime.of(2026, 1, 8, 12, 0),
                        "550e8400-e29b-41d4-a716-446655440000", null, "ID copy", DocumentType.EMPLOYMENT_CERTIFICATE)
        ));
        when(loanRequestRepository.findAllForReportExport()).thenReturn(List.of(
                loanRequest(300L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        RequestStatus.APPROVED, LocalDateTime.of(2026, 1, 9, 8, 0), LocalDateTime.of(2026, 1, 10, 8, 30),
                        "actor-kc-1", new BigDecimal("1200.00"), new BigDecimal("1200.00"), 12,
                        "Need support", LoanType.PERSONAL_ADVANCE)
        ));
        when(authorizationRequestRepository.findAllForReportExport()).thenReturn(List.of(
                authorizationRequest(400L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        RequestStatus.APPROVED, LocalDateTime.of(2026, 1, 11, 7, 30), LocalDateTime.of(2026, 1, 11, 12, 0),
                        "actor-kc-1", AuthorizationType.EQUIPMENT_REQUEST, "Conference room access")
        ));

        HrReportExportRequest request = new HrReportExportRequest();
        request.setSourceTypes(List.of(
                HrReportExportRequest.SourceType.LEAVE,
                HrReportExportRequest.SourceType.DOCUMENT,
                HrReportExportRequest.SourceType.LOAN,
                HrReportExportRequest.SourceType.AUTHORIZATION
        ));
        request.setIncludeSheets(List.of("SUMMARY", "LEAVES", "DOCUMENTS", "LOANS", "AUTHORIZATIONS", "PENDING_AGING", "DECISION_ACTIVITY"));
        request.setTitle("ArabSoft HR Report");

        byte[] bytes = service.exportExcel(request, jwt);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(7);
            assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
            assertThat(workbook.getSheet("Leaves")).isNotNull();
            assertThat(workbook.getSheet("Documents")).isNotNull();
            assertThat(workbook.getSheet("Loans")).isNotNull();
            assertThat(workbook.getSheet("Authorizations")).isNotNull();
            assertThat(workbook.getSheet("Pending Aging")).isNotNull();
            assertThat(workbook.getSheet("Decision Activity")).isNotNull();

            Sheet summary = workbook.getSheet("Summary");
            assertThat(cellValue(summary, 0, 0)).isEqualTo("ArabSoft HR Report");
            assertThat(cellValue(summary, 1, 0)).contains("Detailed employee/request rows");
            assertThat(workbookText(workbook)).contains("Report scope");
            assertThat(workbookText(workbook)).contains("Applied filters");
            assertThat(workbookText(workbook)).contains("Key metrics");
            assertThat(workbookText(workbook)).contains("Department workload top 10");
            assertThat(workbookText(workbook)).contains("Attention items / stale pending preview");

            Sheet leaves = workbook.getSheet("Leaves");
            assertThat(headerValues(leaves.getRow(0))).containsExactly(
                    "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role"
            );
            assertThat(cellValue(leaves, 1, 10)).isEqualTo("Pending");
            assertThat(cellValue(leaves, 1, 11)).isEqualTo("Pending team leader");
            assertThat(cellValue(leaves, 2, 10)).isEqualTo("Approved");
            assertThat(cellValue(leaves, 2, 11)).isEqualTo("Approved");

            Sheet documents = workbook.getSheet("Documents");
            assertThat(cellValue(documents, 1, 10)).isEqualTo("Approved");
            assertThat(cellValue(documents, 1, 11)).isEqualTo("Approved - needs final file");

            String workbookText = workbookText(workbook);
            assertThat(workbookText).contains("ArabSoft HR Report");
            assertThat(workbookText).contains("Amina Ben Ali");
            assertThat(workbookText).contains("HR user");
            assertThat(workbookText).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
        }
    }

    @Test
    void exportExcel_roundsAverageValuesInSummaryAndKeepsDetailedColumns() throws Exception {
        when(hrService.getAllUsersWithDetails()).thenReturn(List.of(
                employeeMap(10L, "Amina", "Ben Ali", "amina@example.test", LocalDate.of(2024, 1, 10),
                        7L, "Engineering", 3L, "Core", "Developer", "EMPLOYEE")
        ));
        when(userRepository.findByKeycloakIdWithPersonAndTeamLeader("actor-kc-1"))
                .thenReturn(Optional.of(actorUser(70L, "Sana", "El Hadi", "sana.hr@example.test", "sana.hr", "HR_MANAGER")));
        when(leaveRequestRepository.findAllForReportExport()).thenReturn(List.of(
                leaveRequest(100L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 1, 9, 0),
                        LocalDateTime.of(2026, 1, 8, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(101L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 2, 9, 0),
                        LocalDateTime.of(2026, 1, 9, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(102L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 3, 9, 0),
                        LocalDateTime.of(2026, 1, 10, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(103L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 4, 9, 0),
                        LocalDateTime.of(2026, 1, 11, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(104L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 5, 9, 0),
                        LocalDateTime.of(2026, 1, 12, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(105L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 6, 9, 0),
                        LocalDateTime.of(2026, 1, 13, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(106L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 7, 9, 0),
                        LocalDateTime.of(2026, 1, 14, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(107L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 8, 9, 0),
                        LocalDateTime.of(2026, 1, 15, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(108L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 9, 9, 0),
                        LocalDateTime.of(2026, 1, 16, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(109L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 10, 9, 0),
                        LocalDateTime.of(2026, 1, 18, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved")
        ));
        HrReportExportRequest request = new HrReportExportRequest();
        request.setSourceTypes(List.of(HrReportExportRequest.SourceType.LEAVE));
        request.setIncludeSheets(List.of("SUMMARY", "LEAVES"));
        request.setTitle("Rounded Summary");

        byte[] bytes = service.exportExcel(request, jwt);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summary = workbook.getSheet("Summary");
            assertThat(cellValue(summary, 0, 0)).isEqualTo("Rounded Summary");
            assertThat(workbookText(workbook)).contains("7.1 days");

            Sheet leaves = workbook.getSheet("Leaves");
            assertThat(headerValues(leaves.getRow(0))).containsExactly(
                    "Employee name", "Username", "Email", "Hire date", "Department", "Team", "Job title", "Role"
            );
        }
    }

    @Test
    void exportExcel_filtersRowsByDepartmentTeamStatusAndDateBasis() throws Exception {
        when(hrService.getAllUsersWithDetails()).thenReturn(List.of(
                employeeMap(10L, "Amina", "Ben Ali", "amina@example.test", LocalDate.of(2024, 1, 10),
                        7L, "Engineering", 3L, "Core", "Developer", "EMPLOYEE"),
                employeeMap(11L, "Ilyes", "Mansouri", "ilyes@example.test", LocalDate.of(2023, 9, 20),
                        8L, "Finance", 4L, "Payroll", "Analyst", "EMPLOYEE")
        ));

        when(leaveRequestRepository.findAllForReportExport()).thenReturn(List.of(
                leaveRequest(100L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                        LeaveStatus.APPROVED, LocalDateTime.of(2026, 1, 5, 9, 0),
                        LocalDateTime.of(2026, 1, 8, 10, 0), "actor-kc-1", null,
                        "Annual leave", ApprovalDecision.APPROVED, ApprovalDecision.APPROVED, "Approved"),
                leaveRequest(101L, employeeUser(11L, "ilyes", TypeRole.EMPLOYEE, 4L, "Payroll"),
                        LeaveStatus.PENDING, LocalDateTime.of(2026, 2, 6, 9, 0),
                        null, null, null,
                        "Training", ApprovalDecision.PENDING, ApprovalDecision.PENDING, null)
        ));
        when(userRepository.findByKeycloakIdWithPersonAndTeamLeader(any())).thenReturn(Optional.empty());

        HrReportExportRequest request = new HrReportExportRequest();
        request.setSourceTypes(List.of(HrReportExportRequest.SourceType.LEAVE));
        request.setDateBasis(HrReportExportRequest.DateBasis.SUBMITTED);
        request.setDateFrom(LocalDate.of(2026, 1, 1));
        request.setDateTo(LocalDate.of(2026, 1, 31));
        request.setStatusGroup(HrReportExportRequest.StatusGroup.APPROVED);
        request.setDepartmentId(7L);
        request.setTeamId(3L);
        request.setIncludeSheets(List.of("SUMMARY", "LEAVES"));

        byte[] bytes = service.exportExcel(request, jwt);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet leaves = workbook.getSheet("Leaves");
            assertThat(leaves.getLastRowNum()).isEqualTo(1);
            assertThat(cellValue(leaves, 1, 0)).isEqualTo("Amina Ben Ali");
            assertThat(workbookText(workbook)).contains("Total requests");
            assertThat(workbookText(workbook)).doesNotContain("Ilyes Mansouri");
        }
    }

    @Test
    void exportExcel_sanitizesSentinelValuesAcrossDetailSheetsAndKeepsNumericMetrics() throws Exception {
        when(hrService.getAllUsersWithDetails()).thenReturn(List.of(
                employeeMap(10L, "Amina", "Ben Ali", "amina@example.test", LocalDate.of(2024, 1, 10),
                        7L, "Engineering", 3L, "Core", "125", "EMPLOYEE"),
                employeeMap(11L, "Ilyes", "Mansouri", "ilyes@example.test", LocalDate.of(2023, 9, 20),
                        117L, "117", 4L, "Payroll", "117", "EMPLOYEE"),
                employeeMap(12L, "Sana", "Hadj", "sana@example.test", LocalDate.of(2022, 5, 12),
                        9L, "Loans", 5L, "Finance", "Developer", "EMPLOYEE"),
                employeeMap(13L, "Leila", "Trabelsi", "leila@example.test", LocalDate.of(2021, 7, 2),
                        10L, "HR", 6L, "Admin", "Coordinator", "EMPLOYEE")
        ));

        LeaveRequest leave = leaveRequest(100L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                LeaveStatus.PENDING, LocalDateTime.of(2026, 1, 5, 9, 0),
                null, null, null,
                "125", ApprovalDecision.PENDING, ApprovalDecision.PENDING, "125");
        leave.setNumberOfDays(3);

        DocumentRequest documentNewest = documentRequest(200L, employeeUser(12L, "sana", TypeRole.EMPLOYEE, 5L, "Finance"),
                RequestStatus.APPROVED, LocalDateTime.of(2026, 1, 13, 11, 0), LocalDateTime.of(2026, 1, 13, 13, 0),
                "actor-kc-1", null, "Real employee note", DocumentType.SALARY_CERTIFICATE);
        documentNewest.setHrNote("Real HR note");

        DocumentRequest documentMiddle = documentRequest(201L, employeeUser(13L, "leila", TypeRole.EMPLOYEE, 6L, "Admin"),
                RequestStatus.REJECTED, LocalDateTime.of(2026, 1, 12, 10, 0), LocalDateTime.of(2026, 1, 12, 11, 0),
                "actor-kc-1", null, "Another employee note", DocumentType.EMPLOYMENT_CERTIFICATE);
        documentMiddle.setHrNote("Another HR note");

        DocumentRequest documentSentinel = documentRequest(202L, employeeUser(11L, "ilyes", TypeRole.EMPLOYEE, 4L, "Payroll"),
                RequestStatus.PENDING, LocalDateTime.of(2026, 1, 11, 9, 0), null,
                null, null, "117", DocumentType.SALARY_CERTIFICATE);
        documentSentinel.setHrNote("117");
        documentSentinel.setNotes("117");

        LoanRequest loanNewest = loanRequest(300L, employeeUser(12L, "sana", TypeRole.EMPLOYEE, 5L, "Finance"),
                RequestStatus.PENDING, LocalDateTime.of(2026, 1, 13, 8, 0), null,
                null, new BigDecimal("5000.00"), new BigDecimal("136"), 12,
                "136", LoanType.PERSONAL_ADVANCE);
        loanNewest.setHrDecisionStage("136");
        loanNewest.setHrNote("136");
        loanNewest.setCancellationReason("136");
        loanNewest.setMeetingAt(null);
        loanNewest.setRiskScore(136);

        LoanRequest loanStageSentinel = loanRequest(301L, employeeUser(10L, "amina", TypeRole.EMPLOYEE, 3L, "Core"),
                RequestStatus.PENDING, LocalDateTime.of(2026, 1, 12, 8, 0), null,
                null, new BigDecimal("2500.00"), null, 6,
                "136", LoanType.PERSONAL_ADVANCE);
        loanStageSentinel.setHrDecisionStage("136");
        loanStageSentinel.setHrNote("136");
        loanStageSentinel.setCancellationReason("136");
        loanStageSentinel.setMeetingAt(null);
        loanStageSentinel.setRiskScore(136);

        AuthorizationRequest authorization = authorizationRequest(400L, employeeUser(13L, "leila", TypeRole.EMPLOYEE, 6L, "Admin"),
                RequestStatus.PENDING, LocalDateTime.of(2026, 1, 11, 7, 30), null,
                null, AuthorizationType.TIME_PERMISSION, "123");
        authorization.setHrNote("123");
        authorization.setReason("123");
        authorization.setAbsenceDate(null);
        authorization.setStartDate(null);
        authorization.setEndDate(null);
        authorization.setFromTime(null);
        authorization.setToTime(null);
        authorization.setEquipmentType("123");

        when(leaveRequestRepository.findAllForReportExport()).thenReturn(List.of(leave));
        when(documentRequestRepository.findAllForReportExport()).thenReturn(List.of(documentNewest, documentMiddle, documentSentinel));
        when(loanRequestRepository.findAllForReportExport()).thenReturn(List.of(loanNewest, loanStageSentinel));
        when(authorizationRequestRepository.findAllForReportExport()).thenReturn(List.of(authorization));

        HrReportExportRequest request = new HrReportExportRequest();
        request.setSourceTypes(List.of(
                HrReportExportRequest.SourceType.LEAVE,
                HrReportExportRequest.SourceType.DOCUMENT,
                HrReportExportRequest.SourceType.LOAN,
                HrReportExportRequest.SourceType.AUTHORIZATION
        ));
        request.setIncludeSheets(List.of("SUMMARY", "LEAVES", "DOCUMENTS", "LOANS", "AUTHORIZATIONS", "PENDING_AGING"));
        request.setTitle("Clean Export");

        byte[] bytes = service.exportExcel(request, jwt);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet leaves = workbook.getSheet("Leaves");
            assertThat(cellValue(leaves, 1, 6)).isBlank();
            assertThat(cellValue(leaves, 1, 13)).isBlank();
            assertThat(cellValue(leaves, 1, 14)).isBlank();
            assertThat(cellValue(leaves, 1, 15)).isBlank();
            assertThat(cellValue(leaves, 1, 17)).isBlank();
            assertThat(cellValue(leaves, 1, 18)).isBlank();
            assertThat(cellValue(leaves, 1, 19)).isBlank();
            assertThat(cellValue(leaves, 1, 10)).isEqualTo("Pending");
            assertThat(cellValue(leaves, 1, 11)).isEqualTo("Pending team leader");
            assertThat(cellValue(leaves, 1, 23)).isEqualTo("3");

            Sheet documents = workbook.getSheet("Documents");
            assertThat(cellValue(documents, 3, 4)).isBlank();
            assertThat(cellValue(documents, 3, 6)).isBlank();
            assertThat(cellValue(documents, 3, 10)).isEqualTo("Pending");
            assertThat(cellValue(documents, 3, 11)).isEqualTo("Pending");
            assertThat(cellValue(documents, 3, 13)).isBlank();
            assertThat(cellValue(documents, 3, 14)).isBlank();
            assertThat(cellValue(documents, 3, 15)).isBlank();
            assertThat(cellValue(documents, 3, 16)).isBlank();
            assertThat(cellValue(documents, 3, 17)).isBlank();
            assertThat(cellValue(documents, 3, 18)).isBlank();
            assertThat(cellValue(documents, 3, 19)).isBlank();

            Sheet loans = workbook.getSheet("Loans");
            assertThat(cellValue(loans, 1, 11)).isBlank();
            assertThat(cellValue(loans, 1, 13)).isBlank();
            assertThat(cellValue(loans, 1, 14)).isBlank();
            assertThat(cellValue(loans, 1, 15)).isBlank();
            assertThat(cellValue(loans, 1, 17)).isBlank();
            assertThat(cellValue(loans, 1, 18)).isBlank();
            assertThat(cellValue(loans, 1, 19)).isBlank();
            assertThat(cellValue(loans, 1, 21)).isEqualTo("5000.00");
            assertThat(cellValue(loans, 1, 22)).isBlank();
            assertThat(cellValue(loans, 1, 23)).isEqualTo("12");
            assertThat(cellValue(loans, 1, 25)).isBlank();
            assertThat(cellValue(loans, 1, 26)).isBlank();
            assertThat(cellValue(loans, 1, 27)).isBlank();
            assertThat(cellValue(loans, 2, 11)).isBlank();
            assertThat(cellValue(loans, 2, 19)).isBlank();
            assertThat(cellValue(loans, 2, 20)).isEqualTo("Personal Advance");
            assertThat(cellValue(loans, 2, 15)).isBlank();
            assertThat(cellValue(loans, 2, 21)).isEqualTo("2500.00");
            assertThat(cellValue(loans, 2, 22)).isBlank();
            assertThat(cellValue(loans, 2, 23)).isEqualTo("6");
            assertThat(cellValue(loans, 2, 25)).isBlank();
            assertThat(cellValue(loans, 2, 26)).isBlank();
            assertThat(cellValue(loans, 2, 27)).isBlank();

            Sheet authorizations = workbook.getSheet("Authorizations");
            assertThat(cellValue(authorizations, 1, 13)).isBlank();
            assertThat(cellValue(authorizations, 1, 14)).isBlank();
            assertThat(cellValue(authorizations, 1, 15)).isBlank();
            assertThat(cellValue(authorizations, 1, 17)).isBlank();
            assertThat(cellValue(authorizations, 1, 18)).isBlank();
            assertThat(cellValue(authorizations, 1, 19)).isBlank();
            assertThat(cellValue(authorizations, 1, 21)).isBlank();
            assertThat(cellValue(authorizations, 1, 22)).isBlank();
            assertThat(cellValue(authorizations, 1, 23)).isBlank();
            assertThat(cellValue(authorizations, 1, 24)).isBlank();
            assertThat(cellValue(authorizations, 1, 25)).isBlank();
            assertThat(cellValue(authorizations, 1, 26)).isBlank();

            String workbookText = workbookText(workbook);
            assertThat(workbookText).doesNotContain("System evaluation");
            assertThat(workbookText).doesNotContain("recommendation");
            assertThat(workbook.getSheet("Pending Aging")).isNotNull();
            assertThat(cellValue(workbook.getSheet("Pending Aging"), 1, 9)).matches("\\d+");
            assertThat(workbookText).contains("3");
            assertThat(workbookText).contains("5000.00");
            assertThat(workbookText).contains("12");
            assertNoNumericOnlyPlaceholders(workbook.getSheet("Leaves"), Set.of(14, 23));
            assertNoNumericOnlyPlaceholders(workbook.getSheet("Documents"), Set.of(14));
            assertNoNumericOnlyPlaceholders(workbook.getSheet("Loans"), Set.of(14, 21, 22, 23, 24, 27));
            assertNoNumericOnlyPlaceholders(workbook.getSheet("Authorizations"), Set.of(14));
        }
    }

    @Test
    void exportExcel_rejectsNonHrManager() {
        User employee = new User();
        employee.setId(2L);
        employee.setUsername("employee");
        employee.setKeycloakId("kc-emp");
        employee.setRole(TypeRole.EMPLOYEE);
        when(authenticatedUserResolver.require(jwt)).thenReturn(employee);

        assertThatThrownBy(() -> service.exportExcel(new HrReportExportRequest(), jwt))
                .isInstanceOf(UnauthorizedException.class);
    }

    private Map<String, Object> employeeMap(Long id, String firstName, String lastName, String email, LocalDate hireDate,
                                            Long departmentId, String department, Long teamId, String teamName,
                                            String jobTitle, String role) {
        Map<String, Object> personalInfo = new HashMap<>();
        personalInfo.put("firstName", firstName);
        personalInfo.put("lastName", lastName);
        personalInfo.put("email", email);
        personalInfo.put("hireDate", hireDate.toString());
        personalInfo.put("departmentId", departmentId);
        personalInfo.put("department", department);
        personalInfo.put("jobTitle", jobTitle);

        Map<String, Object> teamInfo = new HashMap<>();
        teamInfo.put("teamId", teamId);
        teamInfo.put("teamName", teamName);

        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("username", email.substring(0, email.indexOf('@')).replace('.', '_'));
        map.put("role", role);
        map.put("personalInfo", personalInfo);
        map.put("teamInfo", teamInfo);
        return map;
    }

    private User employeeUser(Long id, String username, TypeRole role, Long teamId, String teamName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setKeycloakId("kc-" + username);
        user.setRole(role);
        Team team = new Team();
        team.setId(teamId);
        team.setName(teamName);
        user.setTeam(team);
        return user;
    }

    private User actorUser(Long id, String firstName, String lastName, String email, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setKeycloakId("kc-" + username);
        user.setRole(TypeRole.valueOf(role));
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setEmail(email);
        user.setPerson(person);
        return user;
    }

    private LeaveRequest leaveRequest(Long id, User user, LeaveStatus status, LocalDateTime requestedAt,
                                      LocalDateTime approvedAt, String approvedBy, String rejectedBy,
                                      String reason, ApprovalDecision tlDecision, ApprovalDecision hrDecision,
                                      String decisionReason) {
        LeaveRequest request = new LeaveRequest();
        request.setId(id);
        request.setUser(user);
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartDate(requestedAt.toLocalDate().plusDays(2));
        request.setEndDate(requestedAt.toLocalDate().plusDays(3));
        request.setNumberOfDays(2);
        request.setReason(reason);
        request.setRequestDate(requestedAt);
        request.setTeamLeaderDecision(tlDecision);
        request.setHrDecision(hrDecision);
        request.setStatus(status);
        request.setApprovedAt(approvedAt);
        request.setApprovedBy(approvedBy);
        request.setRejectedBy(rejectedBy);
        request.setDecisionReason(decisionReason);
        return request;
    }

    private DocumentRequest documentRequest(Long id, User user, RequestStatus status, LocalDateTime requestedAt,
                                            LocalDateTime processedAt, String approvedBy, String rejectedBy,
                                            String notes, DocumentType documentType) {
        DocumentRequest request = new DocumentRequest();
        request.setId(id);
        request.setUser(user);
        request.setDocumentType(documentType);
        request.setFulfillmentMode(DocumentFulfillmentMode.UPLOADED);
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        request.setProcessedAt(processedAt);
        request.setApprovedBy(approvedBy);
        request.setRejectedBy(rejectedBy);
        request.setNotes(notes);
        request.setHrNote("Uploaded manually");
        request.setAttachmentUploadedAt(processedAt);
        return request;
    }

    private LoanRequest loanRequest(Long id, User user, RequestStatus status, LocalDateTime requestedAt,
                                    LocalDateTime processedAt, String approvedBy, BigDecimal amount,
                                    BigDecimal approvedAmount, Integer repaymentMonths, String reason,
                                    LoanType loanType) {
        LoanRequest request = new LoanRequest();
        request.setId(id);
        request.setUser(user);
        request.setLoanType(loanType);
        request.setAmount(amount);
        request.setApprovedAmount(approvedAmount);
        request.setRepaymentMonths(repaymentMonths);
        request.setReason(reason);
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        request.setProcessedAt(processedAt);
        request.setApprovedBy(approvedBy);
        request.setMonthlyInstallment(new BigDecimal("100.00"));
        request.setRiskScore(45);
        request.setHrNote("Reviewed");
        return request;
    }

    private AuthorizationRequest authorizationRequest(Long id, User user, RequestStatus status, LocalDateTime requestedAt,
                                                      LocalDateTime processedAt, String approvedBy,
                                                      AuthorizationType authorizationType, String reason) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setId(id);
        request.setUser(user);
        request.setAuthorizationType(authorizationType);
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        request.setProcessedAt(processedAt);
        request.setApprovedBy(approvedBy);
        request.setReason(reason);
        request.setHrNote("Authorized");
        request.setAbsenceDate(requestedAt.toLocalDate().plusDays(1));
        request.setStartDate(requestedAt.toLocalDate());
        request.setEndDate(requestedAt.toLocalDate().plusDays(1));
        request.setFromTime(java.time.LocalTime.of(9, 0));
        request.setToTime(java.time.LocalTime.of(10, 30));
        request.setEquipmentType("Laptop");
        return request;
    }

    private List<String> headerValues(Row row) {
        return List.of(
                cellValue(row, 0), cellValue(row, 1), cellValue(row, 2), cellValue(row, 3),
                cellValue(row, 4), cellValue(row, 5), cellValue(row, 6), cellValue(row, 7)
        );
    }

    private String cellValue(Sheet sheet, int row, int col) {
        return cellValue(sheet.getRow(row), col);
    }

    private String cellValue(Row row, int col) {
        return new DataFormatter().formatCellValue(row.getCell(col));
    }

    private void assertNoNumericOnlyPlaceholders(Sheet sheet, java.util.Set<Integer> allowedNumericColumns) {
        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            short lastCellNum = row.getLastCellNum();
            for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
                if (allowedNumericColumns.contains(columnIndex)) {
                    continue;
                }
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(columnIndex);
                String value = cell == null ? "" : formatter.formatCellValue(cell);
                if (value != null && value.matches("\\d{1,3}")) {
                    throw new AssertionError("Unexpected numeric placeholder '" + value + "' at " + sheet.getSheetName() + "!" + (rowIndex + 1) + ":" + (columnIndex + 1));
                }
            }
        }
    }

    private String workbookText(Workbook workbook) {
        DataFormatter formatter = new DataFormatter();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            for (Row row : sheet) {
                row.forEach(cell -> {
                    String value = formatter.formatCellValue(cell);
                    if (!value.isBlank()) {
                        builder.append(value).append('\n');
                    }
                });
            }
        }
        return builder.toString();
    }
}
