package com.example.HRMS.payroll.repository;

import com.example.HRMS.payroll.entity.PayrollResultLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link PayrollResultLine}. Persistence only. Lines are
 * children of a {@link com.example.HRMS.payroll.entity.PayrollEmployeeResult}
 * and are deleted with it on recalculation.
 */
public interface PayrollResultLineRepository extends JpaRepository<PayrollResultLine, UUID> {

    List<PayrollResultLine> findByPayrollEmployeeResultIdOrderByComponentCodeAsc(
            UUID payrollEmployeeResultId);

    void deleteByPayrollEmployeeResultId(UUID payrollEmployeeResultId);
}
