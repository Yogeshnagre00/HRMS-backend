package com.example.HRMS.tax.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Resolves the canonical current Financial Year key (Data Model 17.1).
 *
 * <p>The current FY is the financial year containing the current business date.
 * The business date is evaluated in the v0 business timezone {@code Asia/Kolkata}
 * (never UTC when that would differ). FY boundaries come from the configured
 * {@code LegalEntity.financial_year_start} (only its month/day matter). The
 * result is formatted as {@code YYYY-YY} (e.g. {@code 2025-26}).
 *
 * <p>A {@link Clock} is injected so the business timezone is explicit and the
 * rule is testable; production uses the system clock.
 */
@Component
public class FinancialYearResolver {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final Clock clock;

    public FinancialYearResolver() {
        this(Clock.system(BUSINESS_ZONE));
    }

    public FinancialYearResolver(Clock clock) {
        this.clock = clock;
    }

    /**
     * Current FY key as {@code YYYY-YY}, given the legal entity's FY start date
     * (only month/day are used). The current FY is the one containing the current
     * business date (evaluated in {@code Asia/Kolkata}).
     */
    public String currentFinancialYear(LocalDate financialYearStart) {
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return financialYearFor(businessDate, financialYearStart);
    }

    /**
     * FY key as {@code YYYY-YY} for the financial year containing an arbitrary
     * date, given the legal entity's FY start (only month/day are used). This is
     * the single FY-derivation rule; {@link #currentFinancialYear} is the
     * special case where the date is today's business date. Used by payroll to
     * derive the FY of a specific payroll month (which is not necessarily the
     * current month) without introducing an alternate FY format or rule.
     */
    public String financialYearFor(LocalDate date, LocalDate financialYearStart) {
        MonthDay startMonthDay = MonthDay.of(
                financialYearStart.getMonth(), financialYearStart.getDayOfMonth());
        LocalDate startThisYear = date.withMonth(startMonthDay.getMonthValue())
                .withDayOfMonth(startMonthDay.getDayOfMonth());

        int startYear = date.isBefore(startThisYear)
                ? date.getYear() - 1
                : date.getYear();
        int endYear = startYear + 1;
        return String.format("%04d-%02d", startYear, endYear % 100);
    }
}
