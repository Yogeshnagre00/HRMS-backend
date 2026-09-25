package com.example.HRMS.statutory.controller;

import com.example.HRMS.statutory.dto.StatutoryDtos.CreatePfRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.CreatePtRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.CreateTdsRuleRequest;
import com.example.HRMS.statutory.dto.StatutoryDtos.StatutoryRuleResponse;
import com.example.HRMS.statutory.dto.StatutoryDtos.UpdateDraftRuleRequest;
import com.example.HRMS.statutory.service.StatutoryRuleReleaseService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statutory rule-value release API (Phase 2). Platform release authority only —
 * every endpoint requires {@code statutory.release}, held solely by SUPER_ADMIN
 * (COMPANY_ADMIN / PAYROLL_ADMIN are forbidden by construction). Manages the
 * PF/PT/TDS rule datasets through DRAFT → VERIFIED → SUPERSEDED.
 *
 * <p>Thin controller; the verification gate, immutability, overlap validation
 * and audit live in {@link StatutoryRuleReleaseService}. No statutory values are
 * created by the server — the release authority supplies the rule payload.
 */
@RestController
@RequestMapping("/api/v1/statutory/rules")
@Tag(name = "Statutory Rule Release",
        description = "Platform-authority PF/PT/TDS statutory rule-value datasets (DRAFT/VERIFIED)")
public class StatutoryRuleReleaseController {

    private final StatutoryRuleReleaseService service;
    private final CurrentUser currentUser;

    public StatutoryRuleReleaseController(StatutoryRuleReleaseService service,
                                          CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    // ---- reads ----------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "List statutory rules for a rule version set")
    public ResponseEntity<List<StatutoryRuleResponse>> list(
            @RequestParam("ruleVersionSetId") UUID ruleVersionSetId) {
        return ResponseEntity.ok(service.listForVersionSet(ruleVersionSetId));
    }

    // ---- PF -------------------------------------------------------------

    @PostMapping("/pf")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Create a DRAFT PF rule")
    public ResponseEntity<StatutoryRuleResponse> createPf(
            @Valid @RequestBody CreatePfRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPfDraft(currentUser.require(), request));
    }

    @PutMapping("/pf/{id}")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Update a DRAFT PF rule")
    public ResponseEntity<StatutoryRuleResponse> updatePf(@PathVariable UUID id,
            @Valid @RequestBody UpdateDraftRuleRequest request) {
        return ResponseEntity.ok(service.updatePfDraft(currentUser.require(), id, request));
    }

    @PostMapping("/pf/{id}/verify")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Verify (release) a DRAFT PF rule")
    public ResponseEntity<StatutoryRuleResponse> verifyPf(@PathVariable UUID id) {
        return ResponseEntity.ok(service.verifyPf(currentUser.require(), id));
    }

    @PostMapping("/pf/{id}/supersede")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Supersede a VERIFIED PF rule")
    public ResponseEntity<StatutoryRuleResponse> supersedePf(@PathVariable UUID id) {
        return ResponseEntity.ok(service.supersedePf(currentUser.require(), id));
    }

    // ---- PT -------------------------------------------------------------

    @PostMapping("/pt")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Create a DRAFT PT rule (per state)")
    public ResponseEntity<StatutoryRuleResponse> createPt(
            @Valid @RequestBody CreatePtRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPtDraft(currentUser.require(), request));
    }

    @PutMapping("/pt/{id}")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Update a DRAFT PT rule")
    public ResponseEntity<StatutoryRuleResponse> updatePt(@PathVariable UUID id,
            @Valid @RequestBody UpdateDraftRuleRequest request) {
        return ResponseEntity.ok(service.updatePtDraft(currentUser.require(), id, request));
    }

    @PostMapping("/pt/{id}/verify")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Verify (release) a DRAFT PT rule")
    public ResponseEntity<StatutoryRuleResponse> verifyPt(@PathVariable UUID id) {
        return ResponseEntity.ok(service.verifyPt(currentUser.require(), id));
    }

    @PostMapping("/pt/{id}/supersede")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Supersede a VERIFIED PT rule")
    public ResponseEntity<StatutoryRuleResponse> supersedePt(@PathVariable UUID id) {
        return ResponseEntity.ok(service.supersedePt(currentUser.require(), id));
    }

    // ---- TDS ------------------------------------------------------------

    @PostMapping("/tds")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Create a DRAFT TDS rule (New Regime automatic v0)")
    public ResponseEntity<StatutoryRuleResponse> createTds(
            @Valid @RequestBody CreateTdsRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTdsDraft(currentUser.require(), request));
    }

    @PutMapping("/tds/{id}")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Update a DRAFT TDS rule")
    public ResponseEntity<StatutoryRuleResponse> updateTds(@PathVariable UUID id,
            @Valid @RequestBody UpdateDraftRuleRequest request) {
        return ResponseEntity.ok(service.updateTdsDraft(currentUser.require(), id, request));
    }

    @PostMapping("/tds/{id}/verify")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Verify (release) a DRAFT TDS rule")
    public ResponseEntity<StatutoryRuleResponse> verifyTds(@PathVariable UUID id) {
        return ResponseEntity.ok(service.verifyTds(currentUser.require(), id));
    }

    @PostMapping("/tds/{id}/supersede")
    @PreAuthorize("hasAuthority('statutory.release')")
    @Operation(summary = "Supersede a VERIFIED TDS rule")
    public ResponseEntity<StatutoryRuleResponse> supersedeTds(@PathVariable UUID id) {
        return ResponseEntity.ok(service.supersedeTds(currentUser.require(), id));
    }
}
