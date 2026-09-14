package com.example.HRMS.company.controller;

import com.example.HRMS.company.dto.LegalEntityDtos.CreateLegalEntityRequest;
import com.example.HRMS.company.dto.LegalEntityDtos.LegalEntityResponse;
import com.example.HRMS.company.dto.LegalEntityDtos.UpdateLegalEntityRequest;
import com.example.HRMS.company.service.LegalEntityService;
import com.example.HRMS.security.core.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legal Entity API (API spec section 7): {@code /api/v1/legal-entities} +
 * {@code /{id}}. v0 permits one active legal entity for the single company; a
 * second active entity is rejected (409). Thin controller; logic lives in
 * {@link LegalEntityService}.
 */
@RestController
@RequestMapping("/api/v1/legal-entities")
@Tag(name = "Legal Entity", description = "Legal entity identity for the v0 company")
public class LegalEntityController {

    private final LegalEntityService legalEntityService;
    private final CurrentUser currentUser;

    public LegalEntityController(LegalEntityService legalEntityService, CurrentUser currentUser) {
        this.legalEntityService = legalEntityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "List legal entities")
    public ResponseEntity<List<LegalEntityResponse>> listLegalEntities() {
        return ResponseEntity.ok(legalEntityService.listLegalEntities());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Create legal entity",
            description = "Creates the v0 legal entity. A second active entity is rejected (409).")
    public ResponseEntity<LegalEntityResponse> createLegalEntity(
            @Valid @RequestBody CreateLegalEntityRequest request) {
        LegalEntityResponse created =
                legalEntityService.createLegalEntity(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Read legal entity")
    public ResponseEntity<LegalEntityResponse> getLegalEntity(@PathVariable UUID id) {
        return ResponseEntity.ok(legalEntityService.getLegalEntity(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('company.admin')")
    @Operation(summary = "Update legal entity")
    public ResponseEntity<LegalEntityResponse> updateLegalEntity(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLegalEntityRequest request) {
        return ResponseEntity.ok(
                legalEntityService.updateLegalEntity(currentUser.require(), id, request));
    }
}
