package tn.isetbizerte.pfe.hrbackend.modules.department.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.CreateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.DepartmentResponse;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.UpdateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.repository.DepartmentRepository;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<DepartmentResponse> listDepartments(boolean activeOnly) {
        List<Department> departments = activeOnly
                ? departmentRepository.findByActiveTrueOrderByNameAsc()
                : departmentRepository.findAllByOrderByNameAsc();

        return departments.stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    public List<DepartmentResponse> listActiveDepartmentsForSignup() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        String normalizedName = normalizeName(request.getName());
        ensureNameAvailable(normalizedName, null);

        Department department = new Department();
        department.setName(normalizedName);
        department.setDescription(normalizeDescription(request.getDescription()));
        department.setActive(true);

        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long departmentId, UpdateDepartmentRequest request) {
        Department department = getDepartmentEntity(departmentId);

        String normalizedName = normalizeName(request.getName());
        ensureNameAvailable(normalizedName, departmentId);

        department.setName(normalizedName);
        department.setDescription(normalizeDescription(request.getDescription()));
        if (request.getActive() != null) {
            department.setActive(request.getActive());
        }

        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse archiveDepartment(Long departmentId) {
        Department department = getDepartmentEntity(departmentId);
        department.setActive(false);
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse activateDepartment(Long departmentId) {
        Department department = getDepartmentEntity(departmentId);
        department.setActive(true);
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    public Department requireDepartmentForEmployment(Long departmentId) {
        Department department = getDepartmentEntity(departmentId);
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new BadRequestException("Department '" + department.getName() + "' is archived and cannot be assigned.");
        }
        return department;
    }

    public Department getDepartmentEntity(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + departmentId));
    }

    private void ensureNameAvailable(String name, Long currentDepartmentId) {
        departmentRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (currentDepartmentId == null || !existing.getId().equals(currentDepartmentId)) {
                throw new BadRequestException("Department '" + name + "' already exists.");
            }
        });
    }

    private String normalizeName(String name) {
        String normalized = name != null ? name.trim() : "";
        if (normalized.isBlank()) {
            throw new BadRequestException("Department name is required.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
