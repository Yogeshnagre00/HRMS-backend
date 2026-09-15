package com.example.HRMS.payroll.repository;

import com.example.HRMS.payroll.entity.PayrollDayResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link PayrollDayResult}. Persistence only. Day results
 * are children of a {@link com.example.HRMS.payroll.entity.PayrollEmployeeResult}
 * and are deleted with it on recalculation.
 */
public interface PayrollDayResultRepository extends JpaRepository<PayrollDayResult, UUID> {

    List<PayrollDayResult> findByPayrollEmployeeResultIdOrderByWorkDateAsc(
            UUID payrollEmployeeResultId);

    void deleteByPayrollEmployeeResultId(UUID payrollEmployeeResultId);
}
