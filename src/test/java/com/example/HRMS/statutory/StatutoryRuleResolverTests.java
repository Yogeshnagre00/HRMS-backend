package com.example.HRMS.statutory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.HRMS.auth.entity.UserStatus;
import com.example.HRMS.common.security.ScopeType;
import com.example.HRMS.regression.RbacTestFixtures;
import com.example.HRMS.statutory.entity.PfApplicability;
import com.example.HRMS.statutory.entity.StatutoryConfiguration;
import com.example.HRMS.statutory.entity.StatutoryRuleType;
import com.example.HRMS.statutory.entity.TdsPolicy;
import com.example.HRMS.statutory.service.ResolvedStatutoryRule;
import com.example.HRMS.statutory.service.StatutoryResolutionStatus;
import com.example.HRMS.statutory.service.StatutoryRuleResolver;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 4 statutory rule-resolution foundation tests. Verifies deterministic,
 * effective-date/version/jurisdiction-aware resolution and explicit
 * missing/unconfirmed/unsupported handling — never a silent zero. Because v0
 * ships no verified statutory rule payloads, the "resolved" cases are driven by
 * VERIFIED test rule rows to prove the resolution contract; no production
 * statutory values are asserted.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatutoryRuleResolverTests {

    private static final LocalDate JUNE_2026 = LocalDate.of(2026, 6, 1);

    @Autowired private RbacTestFixtures fixtures;
    @Autowired private StatutoryRuleResolver resolver;

    private UUID actor;

    @BeforeEach
    void setUp() {
        fixtures.resetIdentityData();
        actor = fixtures.createUser("superadmin", ScopeType.PLATFORM, null, UserStatus.ACTIVE);
        fixtures.assignRole(actor, RbacTestFixtures.ROLE_SUPER_ADMIN);
    }

    private StatutoryConfiguration config(PfApplicability pf, String ptState) {
        StatutoryConfiguration c = new StatutoryConfiguration();
        c.setPfApplicability(pf);
        c.setPtState(ptState);
        c.setTdsPolicy(TdsPolicy.NEW_REGIME_AUTOMATIC_V0);
        return c;
    }

    // ---- PF -------------------------------------------------------------

    @Test
    void pfNotApplicableWhenConfigNo() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.NO, null),
                JUNE_2026);
        assertThat(r.type()).isEqualTo(StatutoryRuleType.PF);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.NOT_APPLICABLE);
        assertThat(r.isComputable()).isFalse();
    }

    @Test
    void pfUnconfirmedIsBlockingNeverZero() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.UNCONFIRMED, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.UNCONFIRMED);
        assertThat(r.isBlocking()).isTrue();
        assertThat(r.isComputable()).isFalse();
    }

    @Test
    void pfApplicableButNoRuleReleaseDataMissing() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        // No PF rule row exists → release data missing, not zero.
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
        assertThat(r.isComputable()).isFalse();
    }

    @Test
    void pfDraftRuleNotUsable() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        fixtures.insertPfRule(vs, "IN", "2026-04-01", null, "DRAFT", "{\"x\":1}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    @Test
    void pfVerifiedRuleCoveringMonthResolves() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        UUID ruleId = fixtures.insertPfRule(vs, "IN", "2026-04-01", null, "VERIFIED",
                "{\"employeeRate\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RESOLVED_VERIFIED);
        assertThat(r.ruleId()).isEqualTo(ruleId);
        assertThat(r.ruleVersionSetId()).isEqualTo(vs);
        assertThat(r.isComputable()).isTrue();
    }

    @Test
    void pfFutureRuleNotSelectedPrematurely() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        // Effective from July 2026 — must not cover a June 2026 payroll.
        fixtures.insertPfRule(vs, "IN", "2026-07-01", null, "VERIFIED", "{\"x\":1}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    @Test
    void pfExpiredRuleNotSelected() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        // Effective window ended May 2026 — must not cover June 2026.
        fixtures.insertPfRule(vs, "IN", "2026-01-01", "2026-05-31", "VERIFIED", "{\"x\":1}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    @Test
    void pfSupersededRuleNotUsable() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        fixtures.insertPfRule(vs, "IN", "2026-04-01", null, "SUPERSEDED", "{\"x\":1}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vs, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    // ---- PT -------------------------------------------------------------

    @Test
    void ptMissingStateUnconfirmed() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePt(vs, null, JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.UNCONFIRMED);
    }

    @Test
    void ptUnsupportedStateBlocking() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePt(vs, "Kerala", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.UNSUPPORTED);
        assertThat(r.isBlocking()).isTrue();
    }

    @Test
    void ptSupportedStateNoRuleReleaseDataMissing() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolvePt(vs, "Maharashtra", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    @Test
    void ptVerifiedRuleForStateResolvesWithPeriodicity() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        UUID ruleId = fixtures.insertPtRule(vs, "IN-MH", "MAHARASHTRA", "MONTHLY", "2026-04-01",
                null, "VERIFIED", "{\"slabs\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolvePt(vs, "Maharashtra", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RESOLVED_VERIFIED);
        assertThat(r.ruleId()).isEqualTo(ruleId);
        assertThat(r.dimension()).isEqualTo("MAHARASHTRA");
    }

    @Test
    void ptTamilNaduHalfYearlyResolvable() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        fixtures.insertPtRule(vs, "IN-TN", "TAMIL_NADU", "HALF_YEARLY", "2026-04-01", null,
                "VERIFIED", "{\"slabs\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolvePt(vs, "Tamil Nadu", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RESOLVED_VERIFIED);
        assertThat(r.dimension()).isEqualTo("TAMIL_NADU");
    }

    @Test
    void ptRuleForDifferentStateNotSelected() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        // Only a Karnataka rule exists; resolving Maharashtra must miss.
        fixtures.insertPtRule(vs, "IN-KA", "KARNATAKA", "MONTHLY", "2026-04-01", null,
                "VERIFIED", "{\"slabs\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolvePt(vs, "Maharashtra", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    // ---- TDS ------------------------------------------------------------

    @Test
    void tdsOldRegimeUnsupported() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolveTds(vs, config(PfApplicability.YES, null),
                "OLD_REGIME", "2026-27", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.UNSUPPORTED);
        assertThat(r.reason()).isEqualTo("TDS_OLD_REGIME_UNSUPPORTED");
    }

    @Test
    void tdsNewRegimeNoRuleReleaseDataMissing() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        ResolvedStatutoryRule r = resolver.resolveTds(vs, config(PfApplicability.YES, null),
                "NEW_REGIME", "2026-27", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    @Test
    void tdsVerifiedRuleForFyResolves() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        UUID ruleId = fixtures.insertTdsRule(vs, "IN", "2026-27", "2026-04-01", null, "VERIFIED",
                "{\"slabs\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolveTds(vs, config(PfApplicability.YES, null),
                "NEW_REGIME", "2026-27", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RESOLVED_VERIFIED);
        assertThat(r.ruleId()).isEqualTo(ruleId);
        assertThat(r.dimension()).isEqualTo("2026-27");
    }

    @Test
    void tdsRuleForDifferentFyNotSelected() {
        UUID vs = fixtures.insertRuleVersionSet("VERIFIED");
        fixtures.insertTdsRule(vs, "IN", "2025-26", "2025-04-01", null, "VERIFIED",
                "{\"slabs\":null}", actor);
        ResolvedStatutoryRule r = resolver.resolveTds(vs, config(PfApplicability.YES, null),
                "NEW_REGIME", "2026-27", JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }

    // ---- version isolation ----------------------------------------------

    @Test
    void ruleUnderDifferentVersionSetNotSelected() {
        UUID vsA = fixtures.insertRuleVersionSet("VERIFIED");
        UUID vsB = fixtures.insertRuleVersionSet("VERIFIED");
        // Rule verified under version set A; resolving with B must not see it.
        fixtures.insertPfRule(vsA, "IN", "2026-04-01", null, "VERIFIED", "{\"x\":1}", actor);
        ResolvedStatutoryRule r = resolver.resolvePf(vsB, config(PfApplicability.YES, null),
                JUNE_2026);
        assertThat(r.status()).isEqualTo(StatutoryResolutionStatus.RELEASE_DATA_MISSING);
    }
}
