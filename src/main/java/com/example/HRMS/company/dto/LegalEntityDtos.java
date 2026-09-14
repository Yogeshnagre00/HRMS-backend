package com.example.HRMS.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response DTOs for the Legal Entity API. Persistence entities are never
 * exposed. Legal Entity owns legal-entity identity only; statutory configuration
 * is owned by the statutory module.
 */
public final class LegalEntityDtos {

    private LegalEntityDtos() {
    }

    public record CreateLegalEntityRequest(
            @NotBlank @Size(max = 255) String legalName,
            @NotBlank @Pattern(regexp = "[A-Z]{2}",
                    message = "countryCode must be a 2-letter ISO code") String countryCode,
            @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
                    message = "pan must be a valid 10-character PAN") String pan,
            @NotNull LocalDate financialYearStart) {
    }

    public record UpdateLegalEntityRequest(
            @NotBlank @Size(max = 255) String legalName,
            @NotBlank @Pattern(regexp = "[A-Z]{2}",
                    message = "countryCode must be a 2-letter ISO code") String countryCode,
            @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
                    message = "pan must be a valid 10-character PAN") String pan,
            @NotNull LocalDate financialYearStart,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE",
                    message = "status must be ACTIVE or INACTIVE") String status) {
    }

    public record LegalEntityResponse(
            UUID id,
            UUID companyId,
            String legalName,
            String countryCode,
            String pan,
            LocalDate financialYearStart,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
