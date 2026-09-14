package com.example.HRMS.tax;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.HRMS.tax.service.FinancialYearResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * V2-003 unit tests for the canonical current-FY derivation (Data Model 17.1):
 * YYYY-YY format, LegalEntity.financial_year_start boundary, Asia/Kolkata
 * business date (not UTC).
 */
class FinancialYearResolverTests {

    private static final LocalDate APRIL_1 = LocalDate.of(2000, 4, 1);

    private FinancialYearResolver resolverAt(ZonedDateTime instant) {
        return new FinancialYearResolver(Clock.fixed(instant.toInstant(), instant.getZone()));
    }

    @Test
    void afterFinancialYearStartUsesCurrentYearAsStart() {
        // 2026-09-14 (Asia/Kolkata), FY start Apr 1 -> 2026-27.
        var resolver = resolverAt(ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0,
                ZoneId.of("Asia/Kolkata")));
        assertThat(resolver.currentFinancialYear(APRIL_1)).isEqualTo("2026-27");
    }

    @Test
    void beforeFinancialYearStartUsesPreviousYearAsStart() {
        // 2026-02-15 (Asia/Kolkata), FY start Apr 1 -> 2025-26.
        var resolver = resolverAt(ZonedDateTime.of(2026, 2, 15, 10, 0, 0, 0,
                ZoneId.of("Asia/Kolkata")));
        assertThat(resolver.currentFinancialYear(APRIL_1)).isEqualTo("2025-26");
    }

    @Test
    void onFinancialYearStartDayUsesCurrentYearAsStart() {
        var resolver = resolverAt(ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0,
                ZoneId.of("Asia/Kolkata")));
        assertThat(resolver.currentFinancialYear(APRIL_1)).isEqualTo("2026-27");
    }

    @Test
    void usesAsiaKolkataBusinessDateNotUtc() {
        // 2026-03-31 20:00 UTC is 2026-04-01 01:30 in Asia/Kolkata (+05:30),
        // so the business date is already in FY 2026-27, not 2025-26.
        var resolver = resolverAt(ZonedDateTime.of(2026, 3, 31, 20, 0, 0, 0, ZoneOffset.UTC));
        assertThat(resolver.currentFinancialYear(APRIL_1)).isEqualTo("2026-27");
    }

    @Test
    void formatIsYyyyDashYy() {
        var resolver = resolverAt(ZonedDateTime.of(2025, 6, 1, 10, 0, 0, 0,
                ZoneId.of("Asia/Kolkata")));
        assertThat(resolver.currentFinancialYear(APRIL_1)).matches("\\d{4}-\\d{2}");
    }
}
