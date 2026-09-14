package com.example.HRMS.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request/response DTOs for the Company API. Persistence entities are never exposed. */
public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 255) String name) {
    }

    public record UpdateCompanyRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE",
                    message = "status must be ACTIVE or INACTIVE") String status) {
    }

    public record CompanyResponse(
            UUID id,
            String name,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
