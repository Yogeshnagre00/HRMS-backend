package com.example.HRMS.csvimport.validation;

import com.example.HRMS.common.api.ApiMessages;
import com.example.HRMS.csvimport.dto.CsvImportDtos.ValidationIssue;
import com.example.HRMS.csvimport.dto.ValidationSeverity;
import com.example.HRMS.statutory.service.SupportedPtStates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates a single parsed CSV data row against the existing authoritative
 * domain rules (reused, not redefined): PAN format, tax regime enum, supported
 * PT states, non-negative monetary {@code decimal(18,2)} / leave quantity
 * {@code decimal(8,2)}, ISO {@code YYYY-MM-DD} dates, exit ≥ joining, and
 * required/nonblank fields. Produces {@link ValidationIssue}s; it does not
 * mutate anything. Old Regime is accepted with a surfaced WARNING (never a
 * rejection). Sensitive values (PAN, account number, UAN) are never echoed into
 * messages.
 */
public final class RowValidator {

    // Reused from the existing employee/bank domain contracts.
    private static final java.util.regex.Pattern PAN_PATTERN =
            java.util.regex.Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private RowValidator() {
    }

    /** Validate one data row (already mapped header→value). rowNumber is physical. */
    public static List<ValidationIssue> validate(int rowNumber, Map<String, String> row) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Identity
        requireNonBlank(issues, rowNumber, CsvHeaders.EMPLOYEE_ID, row);
        requireNonBlank(issues, rowNumber, CsvHeaders.NAME, row);
        requireNonBlank(issues, rowNumber, CsvHeaders.EMPLOYMENT_TYPE, row);

        LocalDate joining = validateDate(issues, rowNumber, CsvHeaders.JOINING_DATE, row, true);
        LocalDate exit = validateDate(issues, rowNumber, CsvHeaders.EXIT_DATE, row, false);
        if (joining != null && exit != null && exit.isBefore(joining)) {
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.EXIT_DATE,
                    CsvIssueCodes.EXIT_BEFORE_JOINING, ApiMessages.CSV_EXIT_BEFORE_JOINING,
                    ValidationSeverity.BLOCKING));
        }

        // Statutory identity
        validatePan(issues, rowNumber, row);
        validatePtState(issues, rowNumber, row);
        validateTaxRegime(issues, rowNumber, row);

        // Opening tax state (required, non-negative, 2dp)
        validateMoney(issues, rowNumber, CsvHeaders.CUMULATIVE_TAXABLE_INCOME, row, true);
        validateMoney(issues, rowNumber, CsvHeaders.TDS_ALREADY_DEDUCTED, row, true);

        // Bank (required; no value echoed)
        requireNonBlankCoded(issues, rowNumber, CsvHeaders.ACCOUNT_NUMBER, row,
                CsvIssueCodes.INVALID_ACCOUNT_NUMBER, ApiMessages.CSV_INVALID_ACCOUNT_NUMBER);
        requireNonBlankCoded(issues, rowNumber, CsvHeaders.IFSC, row,
                CsvIssueCodes.INVALID_IFSC, ApiMessages.CSV_INVALID_IFSC);

        // Compensation (required, non-negative, 2dp)
        validateMoney(issues, rowNumber, CsvHeaders.CTC, row, true);
        validateMoney(issues, rowNumber, CsvHeaders.BASIC, row, true);
        validateMoney(issues, rowNumber, CsvHeaders.HRA, row, true);
        validateMoney(issues, rowNumber, CsvHeaders.DEARNESS_ALLOWANCE, row, true);
        validateMoney(issues, rowNumber, CsvHeaders.OTHER_ALLOWANCES, row, true);
        validateDate(issues, rowNumber, CsvHeaders.EFFECTIVE_DATE, row, true);

        // Opening leave balance (required, non-negative quantity, 2dp)
        validateQuantity(issues, rowNumber, CsvHeaders.OPENING_LEAVE_BALANCE, row);

        return issues;
    }

    private static void requireNonBlank(List<ValidationIssue> issues, int rowNumber,
                                        String field, Map<String, String> row) {
        requireNonBlankCoded(issues, rowNumber, field, row,
                CsvIssueCodes.REQUIRED_FIELD_MISSING, ApiMessages.CSV_REQUIRED_FIELD_MISSING);
    }

    private static void requireNonBlankCoded(List<ValidationIssue> issues, int rowNumber,
                                             String field, Map<String, String> row,
                                             String code, String message) {
        String v = row.get(field);
        if (v == null || v.isBlank()) {
            issues.add(new ValidationIssue(rowNumber, field, code, message,
                    ValidationSeverity.BLOCKING));
        }
    }

    private static LocalDate validateDate(List<ValidationIssue> issues, int rowNumber,
                                          String field, Map<String, String> row, boolean required) {
        String v = row.get(field);
        if (v == null || v.isBlank()) {
            if (required) {
                issues.add(new ValidationIssue(rowNumber, field,
                        CsvIssueCodes.REQUIRED_FIELD_MISSING,
                        ApiMessages.CSV_REQUIRED_FIELD_MISSING, ValidationSeverity.BLOCKING));
            }
            return null;
        }
        try {
            return LocalDate.parse(v.trim(), ISO_DATE);
        } catch (Exception ex) {
            issues.add(new ValidationIssue(rowNumber, field, CsvIssueCodes.INVALID_DATE,
                    ApiMessages.CSV_INVALID_DATE, ValidationSeverity.BLOCKING));
            return null;
        }
    }

    private static void validatePan(List<ValidationIssue> issues, int rowNumber,
                                    Map<String, String> row) {
        String v = row.get(CsvHeaders.PAN);
        if (v == null || v.isBlank()) {
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.PAN,
                    CsvIssueCodes.REQUIRED_FIELD_MISSING, ApiMessages.CSV_REQUIRED_FIELD_MISSING,
                    ValidationSeverity.BLOCKING));
        } else if (!PAN_PATTERN.matcher(v.trim()).matches()) {
            // Message does not echo the value.
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.PAN, CsvIssueCodes.INVALID_PAN,
                    ApiMessages.CSV_INVALID_PAN, ValidationSeverity.BLOCKING));
        }
    }

    private static void validatePtState(List<ValidationIssue> issues, int rowNumber,
                                        Map<String, String> row) {
        String v = row.get(CsvHeaders.PT_STATE);
        // PT State is conditional/optional at the row level; only a supplied,
        // unsupported value is rejected (Business Rules §7.2).
        if (v != null && !v.isBlank() && !SupportedPtStates.isSupported(v.trim())) {
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.PT_STATE,
                    CsvIssueCodes.INVALID_PT_STATE, ApiMessages.CSV_INVALID_PT_STATE,
                    ValidationSeverity.BLOCKING));
        }
    }

    private static void validateTaxRegime(List<ValidationIssue> issues, int rowNumber,
                                          Map<String, String> row) {
        String v = row.get(CsvHeaders.TAX_REGIME);
        if (v == null || v.isBlank()) {
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.TAX_REGIME,
                    CsvIssueCodes.REQUIRED_FIELD_MISSING, ApiMessages.CSV_REQUIRED_FIELD_MISSING,
                    ValidationSeverity.BLOCKING));
            return;
        }
        String t = v.trim();
        if (t.equals("NEW_REGIME")) {
            return;
        }
        if (t.equals("OLD_REGIME")) {
            // Accepted, not rejected; surfaced as a WARNING (established rule).
            issues.add(new ValidationIssue(rowNumber, CsvHeaders.TAX_REGIME,
                    CsvIssueCodes.OLD_REGIME_TDS_UNSUPPORTED,
                    ApiMessages.CSV_OLD_REGIME_TDS_UNSUPPORTED, ValidationSeverity.WARNING));
            return;
        }
        issues.add(new ValidationIssue(rowNumber, CsvHeaders.TAX_REGIME,
                CsvIssueCodes.INVALID_TAX_REGIME, ApiMessages.CSV_INVALID_TAX_REGIME,
                ValidationSeverity.BLOCKING));
    }

    private static void validateMoney(List<ValidationIssue> issues, int rowNumber, String field,
                                      Map<String, String> row, boolean required) {
        validateDecimal(issues, rowNumber, field, row, required, 2,
                CsvIssueCodes.INVALID_AMOUNT, ApiMessages.CSV_INVALID_AMOUNT,
                CsvIssueCodes.NEGATIVE_AMOUNT, ApiMessages.CSV_NEGATIVE_AMOUNT);
    }

    private static void validateQuantity(List<ValidationIssue> issues, int rowNumber, String field,
                                         Map<String, String> row) {
        validateDecimal(issues, rowNumber, field, row, true, 2,
                CsvIssueCodes.INVALID_QUANTITY, ApiMessages.CSV_INVALID_QUANTITY,
                CsvIssueCodes.NEGATIVE_QUANTITY, ApiMessages.CSV_NEGATIVE_QUANTITY);
    }

    private static void validateDecimal(List<ValidationIssue> issues, int rowNumber, String field,
                                        Map<String, String> row, boolean required, int maxScale,
                                        String invalidCode, String invalidMsg,
                                        String negativeCode, String negativeMsg) {
        String v = row.get(field);
        if (v == null || v.isBlank()) {
            if (required) {
                issues.add(new ValidationIssue(rowNumber, field,
                        CsvIssueCodes.REQUIRED_FIELD_MISSING,
                        ApiMessages.CSV_REQUIRED_FIELD_MISSING, ValidationSeverity.BLOCKING));
            }
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(v.trim());
        } catch (NumberFormatException ex) {
            issues.add(new ValidationIssue(rowNumber, field, invalidCode, invalidMsg,
                    ValidationSeverity.BLOCKING));
            return;
        }
        if (amount.scale() > maxScale) {
            issues.add(new ValidationIssue(rowNumber, field, invalidCode, invalidMsg,
                    ValidationSeverity.BLOCKING));
            return;
        }
        if (amount.signum() < 0) {
            issues.add(new ValidationIssue(rowNumber, field, negativeCode, negativeMsg,
                    ValidationSeverity.BLOCKING));
        }
    }
}
