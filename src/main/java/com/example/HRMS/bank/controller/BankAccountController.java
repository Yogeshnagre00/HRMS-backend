package com.example.HRMS.bank.controller;

import com.example.HRMS.bank.dto.BankAccountDtos.BankAccountResponse;
import com.example.HRMS.bank.dto.BankAccountDtos.UpsertBankAccountRequest;
import com.example.HRMS.bank.service.BankAccountService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee Bank Account API (API spec §10): {@code GET} and {@code PUT} on the
 * employee-associated singular bank-account resource. Company-scoped, so it
 * reuses the {@code company.admin} permission. Thin controller; scope isolation,
 * masking and auditing live in {@link BankAccountService}.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/bank-account")
@Tag(name = "Employee Bank Account", description = "Employee bank details for payout export")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final CurrentUser currentUser;

    public BankAccountController(BankAccountService bankAccountService, CurrentUser currentUser) {
        this.bankAccountService = bankAccountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read employee bank details (account number masked)")
    public ResponseEntity<BankAccountResponse> getBankAccount(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(bankAccountService.getBankAccount(currentUser.require(), employeeId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create or update the employee's effective bank details")
    public ResponseEntity<BankAccountResponse> upsertBankAccount(@PathVariable UUID employeeId,
            @Valid @RequestBody UpsertBankAccountRequest request) {
        return ResponseEntity.ok(
                bankAccountService.upsertBankAccount(currentUser.require(), employeeId, request));
    }
}
