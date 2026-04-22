package tn.isetbizerte.pfe.hrbackend.modules.requests.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.util.List;

@Repository
public interface StoredEmployeeDocumentRepository extends JpaRepository<StoredEmployeeDocument, Long> {
    List<StoredEmployeeDocument> findByEmployeeAndActiveTrueOrderByUploadedAtDesc(User employee);
    List<StoredEmployeeDocument> findByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(User employee, DocumentType documentType);
}
