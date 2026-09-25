package com.example.HRMS.statutory.service;

import com.example.HRMS.statutory.entity.PfApplicability;
import com.example.HRMS.statutory.entity.PtRuleState;
import com.example.HRMS.statutory.entity.RuleVersionSetStatus;
import com.example.HRMS.statutory.entity.StatutoryConfiguration;
import com.example.HRMS.statutory.entity.StatutoryPfRule;
import com.example.HRMS.statutory.entity.StatutoryPtRule;
import com.example.HRMS.statutory.entity.StatutoryRuleType;
import com.example.HRMS.statutory.entity.StatutoryTdsRule;
import com.example.HRMS.statutory.entity.TdsPolicy;
import com.example.HRMS.statutory.repository.StatutoryPfRuleRepository;
import com.example.HRMS.statutory.repository.StatutoryPtRuleRepository;
import com.example.HRMS.statutory.repository.StatutoryTdsRuleRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statutory rule-resolution foundation (Phase 4). Given a payroll run's fixed
 * statutory rule-version set, the effective {@link StatutoryConfiguration} and
 * the payroll month, deterministically resolves the PF/PT/TDS rule that would be
 * used — or explains, explicitly, why no computable rule is available.
 *
 * <p>Resolution is effective-date aware, version-aware, jurisdiction-aware and
 * deterministic. It NEVER falls back from an unknown applicability to NO and
 * NEVER substitutes a zero result: an unresolved/unsupported/missing-data
 * condition is returned as an explicit {@link StatutoryResolutionStatus} for the
 * payroll/health-check slice to act on. It reads only the V19 rule tables and
 * fabricates no statutory values.
 *
 * <p>Because verified statutory release data is not present in v0 (the rule
 * tables are empty), an applicable PF/PT/TDS resolves to
 * {@link StatutoryResolutionStatus#RELEASE_DATA_MISSING}. Numeric PF/PT/TDS
 * computation is intentionally deferred until VERIFIED rule payloads exist.
 */
@Service
public class StatutoryRuleResolver {

    private final StatutoryPfRuleRepository pfRepository;
    private final StatutoryPtRuleRepository ptRepository;
    private final StatutoryTdsRuleRepository tdsRepository;

    public StatutoryRuleResolver(StatutoryPfRuleRepository pfRepository,
                                 StatutoryPtRuleRepository ptRepository,
                                 StatutoryTdsRuleRepository tdsRepository) {
        this.pfRepository = pfRepository;
        this.ptRepository = ptRepository;
        this.tdsRepository = tdsRepository;
    }

    /**
     * Resolve PF for the payroll month. Applicability comes from configuration
     * (YES/NO/UNCONFIRMED); a VERIFIED PF rule under the run's rule-version set
     * that covers the payroll month is required to be computable.
     */
    @Transactional(readOnly = true)
    public ResolvedStatutoryRule resolvePf(UUID ruleVersionSetId, StatutoryConfiguration config,
                                           LocalDate payrollMonth) {
        PfApplicability applicability = config.getPfApplicability();
        if (applicability == PfApplicability.NO) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.PF,
                    StatutoryResolutionStatus.NOT_APPLICABLE, ruleVersionSetId, null,
                    "PF_NOT_APPLICABLE");
        }
        if (applicability == PfApplicability.UNCONFIRMED) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.PF,
                    StatutoryResolutionStatus.UNCONFIRMED, ruleVersionSetId, null,
                    "PF_APPLICABILITY_UNCONFIRMED");
        }
        // Applicability YES → require a VERIFIED, effective PF rule.
        Optional<StatutoryPfRule> rule = pfRepository.findByRuleVersionSetId(ruleVersionSetId);
        if (rule.isEmpty()) {
            return releaseDataMissing(StatutoryRuleType.PF, ruleVersionSetId, null);
        }
        StatutoryPfRule r = rule.get();
        if (!isUsable(r.getReleaseStatus(), r.getEffectiveFrom(), r.getEffectiveTo(),
                r.getRulePayload(), payrollMonth)) {
            return releaseDataMissing(StatutoryRuleType.PF, ruleVersionSetId, null);
        }
        return new ResolvedStatutoryRule(StatutoryRuleType.PF,
                StatutoryResolutionStatus.RESOLVED_VERIFIED, ruleVersionSetId, r.getId(),
                r.getRulePayload(), r.getEffectiveFrom(), r.getEffectiveTo(), null,
                "PF_RESOLVED");
    }

    /**
     * Resolve PT for the payroll month. The employee/legal-entity PT state
     * (supplied resolved) drives selection; an unsupported state is blocking, a
     * missing state is unconfirmed, and a supported state requires a VERIFIED PT
     * rule for that state covering the payroll month.
     */
    @Transactional(readOnly = true)
    public ResolvedStatutoryRule resolvePt(UUID ruleVersionSetId, String ptStateName,
                                           LocalDate payrollMonth) {
        if (ptStateName == null || ptStateName.isBlank()) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.PT,
                    StatutoryResolutionStatus.UNCONFIRMED, ruleVersionSetId, null,
                    "PT_STATE_MISSING");
        }
        PtRuleState state = mapState(ptStateName);
        if (state == null) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.PT,
                    StatutoryResolutionStatus.UNSUPPORTED, ruleVersionSetId, ptStateName,
                    "PT_STATE_UNSUPPORTED");
        }
        Optional<StatutoryPtRule> rule = ptRepository.findByRuleVersionSetId(ruleVersionSetId)
                .stream().filter(pr -> pr.getPtState() == state).findFirst();
        if (rule.isEmpty()) {
            return releaseDataMissing(StatutoryRuleType.PT, ruleVersionSetId, state.name());
        }
        StatutoryPtRule r = rule.get();
        if (!isUsable(r.getReleaseStatus(), r.getEffectiveFrom(), r.getEffectiveTo(),
                r.getRulePayload(), payrollMonth)) {
            return releaseDataMissing(StatutoryRuleType.PT, ruleVersionSetId, state.name());
        }
        return new ResolvedStatutoryRule(StatutoryRuleType.PT,
                StatutoryResolutionStatus.RESOLVED_VERIFIED, ruleVersionSetId, r.getId(),
                r.getRulePayload(), r.getEffectiveFrom(), r.getEffectiveTo(), state.name(),
                "PT_RESOLVED");
    }

    /**
     * Resolve TDS for the payroll month. v0 supports New-Regime automatic TDS
     * only; Old-Regime automatic TDS is unsupported (blocking). A VERIFIED TDS
     * rule for the run's financial year covering the payroll month is required.
     */
    @Transactional(readOnly = true)
    public ResolvedStatutoryRule resolveTds(UUID ruleVersionSetId, StatutoryConfiguration config,
                                            String employeeTaxRegime, String financialYear,
                                            LocalDate payrollMonth) {
        // v0 automatic TDS supports NEW_REGIME only; Old Regime automatic is unsupported.
        if (employeeTaxRegime != null && "OLD_REGIME".equals(employeeTaxRegime)) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.TDS,
                    StatutoryResolutionStatus.UNSUPPORTED, ruleVersionSetId, financialYear,
                    "TDS_OLD_REGIME_UNSUPPORTED");
        }
        if (config.getTdsPolicy() != TdsPolicy.NEW_REGIME_AUTOMATIC_V0) {
            return ResolvedStatutoryRule.of(StatutoryRuleType.TDS,
                    StatutoryResolutionStatus.UNSUPPORTED, ruleVersionSetId, financialYear,
                    "TDS_POLICY_UNSUPPORTED");
        }
        Optional<StatutoryTdsRule> rule = tdsRepository.findByRuleVersionSetId(ruleVersionSetId)
                .filter(tr -> financialYear != null && financialYear.equals(tr.getFinancialYear()));
        if (rule.isEmpty()) {
            return releaseDataMissing(StatutoryRuleType.TDS, ruleVersionSetId, financialYear);
        }
        StatutoryTdsRule r = rule.get();
        if (!isUsable(r.getReleaseStatus(), r.getEffectiveFrom(), r.getEffectiveTo(),
                r.getRulePayload(), payrollMonth)) {
            return releaseDataMissing(StatutoryRuleType.TDS, ruleVersionSetId, financialYear);
        }
        return new ResolvedStatutoryRule(StatutoryRuleType.TDS,
                StatutoryResolutionStatus.RESOLVED_VERIFIED, ruleVersionSetId, r.getId(),
                r.getRulePayload(), r.getEffectiveFrom(), r.getEffectiveTo(), financialYear,
                "TDS_RESOLVED");
    }

    // ---- shared ---------------------------------------------------------

    /**
     * A rule is usable only if it is VERIFIED, its effective window covers the
     * payroll month, and it carries a non-empty payload. DRAFT rules are never
     * used for calculation; SUPERSEDED rules are not selected for a new month.
     */
    private boolean isUsable(RuleVersionSetStatus releaseStatus, LocalDate effectiveFrom,
                             LocalDate effectiveTo, String payload, LocalDate payrollMonth) {
        if (releaseStatus != RuleVersionSetStatus.VERIFIED) {
            return false;
        }
        if (payload == null || payload.isBlank()) {
            return false;
        }
        return coversMonth(effectiveFrom, effectiveTo, payrollMonth);
    }

    /** The payroll month (its first day) must fall within [effectiveFrom, effectiveTo]. */
    private boolean coversMonth(LocalDate effectiveFrom, LocalDate effectiveTo,
                                LocalDate payrollMonth) {
        LocalDate monthStart = payrollMonth.withDayOfMonth(1);
        boolean fromOk = !monthStart.isBefore(effectiveFrom);
        boolean toOk = effectiveTo == null || !monthStart.isAfter(effectiveTo);
        return fromOk && toOk;
    }

    private ResolvedStatutoryRule releaseDataMissing(StatutoryRuleType type, UUID ruleVersionSetId,
                                                     String dimension) {
        return ResolvedStatutoryRule.of(type, StatutoryResolutionStatus.RELEASE_DATA_MISSING,
                ruleVersionSetId, dimension, type.name() + "_RELEASE_DATA_MISSING");
    }

    /** Map a human PT state name to the enum; null if unsupported. */
    private PtRuleState mapState(String stateName) {
        return switch (stateName) {
            case "Maharashtra" -> PtRuleState.MAHARASHTRA;
            case "Karnataka" -> PtRuleState.KARNATAKA;
            case "Tamil Nadu" -> PtRuleState.TAMIL_NADU;
            case "Telangana" -> PtRuleState.TELANGANA;
            case "West Bengal" -> PtRuleState.WEST_BENGAL;
            default -> null;
        };
    }
}
