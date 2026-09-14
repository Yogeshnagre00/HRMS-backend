package com.example.HRMS.bank.service;

import com.example.HRMS.audit.service.AuditActions;
import com.example.HRMS.audit.service.AuditService;
import com.example.HRMS.audit.service.AuditService.AuditEvent;
import com.example.HRMS.bank.dto.BankAccountDtos.BankAccountResponse;
import com.example.HRMS.bank.dto.BankAccountDtos.UpsertBankAccountRequest;
import com.example.HRMS.bank.entity.BankAccountStatus;
import com.example.HRMS.bank.entity.EmployeeBankAccount;
import com.example.HRMS.bank.repository.EmployeeBankAccountRepository;
import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.common.security.AuthenticatedUser;
import com.example.HRMS.employee.entity.Employee;
import com.example.HRMS.employee.service.EmployeeService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee bank account use cases: read and create/update the effective bank
 * account for an employee (API spec §10).
 *
 * <p>The employee is always resolved through {@link EmployeeService} scope
 * resolution first, so a caller can only touch bank details for an employee in
 * its own company (cross-company access yields 404, no disclosure). v0 keeps a
 * single current (open) primary account per employee; PUT updates it in place or
 * creates it. The account number is sensitive: it is never logged or placed in
 * audit metadata (audit references the bank-account id), and the read API
 * returns only a masked value. Material changes are audited.
 */
@Service
public class BankAccountService {

    private final EmployeeBankAccountRepository bankAccountRepository;
    private final EmployeeService employeeService;
    private final AuditService auditService;

    public BankAccountService(EmployeeBankAccountRepository bankAccountRepository,
                              EmployeeService employeeService,
                              AuditService auditService) {
        this.bankAccountRepository = bankAccountRepository;
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    /** Read the employee's current effective bank account. 404 if none set. */
    @Transactional(readOnly = true)
    public BankAccountResponse getBankAccount(AuthenticatedUser actor, UUID employeeId) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);
        EmployeeBankAccount account = bankAccountRepository
                .findFirstByEmployeeIdAndEffectiveToIsNull(employee.getId())
                .orElseThrow(() -> ApiException.notFound(ApiMessages.BANK_ACCOUNT_NOT_FOUND));
        return toResponse(account);
    }

    /**
     * Create or update the employee's current effective bank account. If a
     * current (open) account exists it is updated in place; otherwise one is
     * created as the primary active account.
     */
    @Transactional
    public BankAccountResponse upsertBankAccount(AuthenticatedUser actor, UUID employeeId,
                                                 UpsertBankAccountRequest request) {
        Employee employee = employeeService.resolveScopedEmployee(actor, employeeId);

        boolean creating = bankAccountRepository
                .findFirstByEmployeeIdAndEffectiveToIsNull(employee.getId()).isEmpty();
        EmployeeBankAccount account = bankAccountRepository
                .findFirstByEmployeeIdAndEffectiveToIsNull(employee.getId())
                .orElseGet(EmployeeBankAccount::new);
        if (creating) {
            account.setId(UUID.randomUUID());
            account.setEmployeeId(employee.getId());
        }
        account.setAccountNumber(request.accountNumber());
        account.setIfsc(request.ifsc());
        account.setAccountHolderName(request.accountHolderName());
        account.setPrimary(true);
        account.setEffectiveFrom(request.effectiveFrom() != null
                ? request.effectiveFrom() : LocalDate.now());
        account.setEffectiveTo(null);
        account.setStatus(BankAccountStatus.ACTIVE);
        bankAccountRepository.save(account);

        // Audit references the bank-account id only; the account number (sensitive)
        // is never placed in audit metadata. company_id is the actor's tenant.
        auditService.record(new AuditEvent(actor.userId(), actor.companyId(), actor.scopeType(),
                creating ? AuditActions.BANK_ACCOUNT_CREATED : AuditActions.BANK_ACCOUNT_UPDATED,
                AuditActions.ENTITY_EMPLOYEE_BANK_ACCOUNT, account.getId(),
                "SUCCESS", null, null));
        return toResponse(account);
    }

    private static BankAccountResponse toResponse(EmployeeBankAccount a) {
        return new BankAccountResponse(a.getId(), a.getEmployeeId(),
                mask(a.getAccountNumber()), a.getIfsc(), a.getAccountHolderName(),
                a.isPrimary(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getStatus().name());
    }

    /** Mask all but the last four characters of the account number for API output. */
    private static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        int visible = 4;
        if (accountNumber.length() <= visible) {
            return "*".repeat(accountNumber.length());
        }
        int maskedLen = accountNumber.length() - visible;
        return "*".repeat(maskedLen) + accountNumber.substring(maskedLen);
    }
}
