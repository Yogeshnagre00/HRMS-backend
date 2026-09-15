package com.example.HRMS.csvimport.controller;

import com.example.HRMS.common.api.ApiException;
import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.csvimport.dto.CsvImportDtos.ValidationResultResponse;
import com.example.HRMS.csvimport.service.CsvValidationService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Employee CSV import API (API §11.1). V2-005 implements upload+validation
 * (`POST`) and validation-session retrieval (`GET`). Confirmation
 * (`POST .../confirm`) is intentionally NOT implemented (V2-006). Company-scoped,
 * reusing the {@code company.admin} permission. Thin controller; parsing,
 * validation, session persistence and isolation live in
 * {@link CsvValidationService}. No business data is created or modified.
 */
@RestController
@RequestMapping("/api/v1/employees/imports")
@Tag(name = "Employee CSV Import", description = "Upload + validate an employee CSV (V2-005)")
public class CsvImportController {

    private final CsvValidationService service;
    private final CurrentUser currentUser;

    public CsvImportController(CsvValidationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Upload and validate an employee CSV; create a validation session")
    public ResponseEntity<ValidationResultResponse> validate(
            @RequestParam("file") MultipartFile file) {
        if (file == null) {
            throw ApiException.badRequest(ApiMessages.CSV_FILE_REQUIRED);
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw ApiException.badRequest(ApiMessages.CSV_FILE_UNREADABLE);
        }
        // Empty file is handled by the service as a VALIDATION_FAILED result,
        // not as a 400 — the endpoint always returns a validation result.
        ValidationResultResponse result =
                service.validate(currentUser.require(), file.getOriginalFilename(), content);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{importId}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read the validation result/status of an import session")
    public ResponseEntity<ValidationResultResponse> getSession(@PathVariable UUID importId) {
        return ResponseEntity.ok(service.getSession(currentUser.require(), importId));
    }
}
