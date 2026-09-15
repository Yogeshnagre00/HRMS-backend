package com.example.HRMS.csvimport.dto;

/**
 * Shared validation issue severity (Data Model HealthCheckFinding taxonomy,
 * API §11.4). Reuses the project's established three-level model.
 */
public enum ValidationSeverity {
    BLOCKING,
    WARNING,
    INFORMATIONAL
}
