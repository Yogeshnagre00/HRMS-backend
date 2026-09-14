package com.example.HRMS.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request/response DTOs for the Employee Bank Account API (API spec §10).
 * Persistence entities are never exposed.
 *
 * <p>The response deliberately returns a <em>masked</em> account number (last
 * four digits only), honoring the Data Model's "masked UI" intent so the full
 * sensitive value is not transmitted over the read API. The full value remains
 * stored for the downstream bank-transfer export. Only required/optional fields
 * defined by the authoritative Data Model are present; no bank name, branch,
 * account type or verification status is invented.
 */
public final class BankAccountDtos {

    private BankAccountDtos() {
    }

    /**
     * Create/update the employee's effective bank account. {@code effectiveFrom}
     * is optional; when omitted the server uses the current date. The account is
     * always the primary active account in v0 (single current account per
     * employee), so caller-supplied primary/status flags are not accepted.
     */
    public record UpsertBankAccountRequest(
            @NotBlank @Size(max = 34) String accountNumber,
            @NotBlank @Size(max = 20) String ifsc,
            @Size(max = 255) String accountHolderName,
            LocalDate effectiveFrom) {
    }

    /**
     * Bank account view. {@code accountNumberMasked} shows only the last four
     * digits; the full number is never returned by the API.
     */
    public record BankAccountResponse(
            UUID id,
            UUID employeeId,
            String accountNumberMasked,
            String ifsc,
            String accountHolderName,
            boolean primary,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status) {
    }
}
