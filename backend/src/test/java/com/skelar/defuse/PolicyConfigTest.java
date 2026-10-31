package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.config.policy.PolicyProperties;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** defuse-policy.yml: loads, validates, matches the Python reference, and every value cites evidence. */
@SpringBootTest
class PolicyConfigTest {
    @Autowired PolicyProperties props;
    @Autowired ReferencePolicyService policy;

    @EnableConfigurationProperties(PolicyProperties.class)
    static class Bind {
    }

    @Test
    void loadedYamlEqualsDefaults() {
        assertThat(props.refund()).isEqualTo(PolicyProperties.defaults().refund());
        assertThat(props.retention()).isEqualTo(PolicyProperties.defaults().retention());
        assertThat(props.risk()).isEqualTo(PolicyProperties.defaults().risk());
        assertThat(props.money()).isEqualTo(PolicyProperties.defaults().money());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedStaticsMirrorConfig() {   // until executor/reply code reads the getters
        assertThat(ReferencePolicyService.DISCOUNT_PERCENT).isEqualTo(policy.retentionDiscountPercent());
        assertThat(ReferencePolicyService.DISCOUNT_MONTHS).isEqualTo(policy.retentionDiscountMonths());
        assertThat(ReferencePolicyService.PAUSE_MONTHS).isEqualTo(policy.retentionPauseMonths());
    }

    @Test
    void everyRuleCitesEvidenceThatExists() throws Exception {
        String rules = Files.readString(Path.of("../docs/evidence/RULES.md"));
        for (String key : PolicyProperties.REQUIRED_SOURCES) {
            assertThat(props.sources().get(key)).as(key).startsWith("RULES.md#");
        }
        for (String anchor : new String[] {"refund", "retention", "money", "risk"}) {
            assertThat(rules).contains(anchor);
        }
    }

    @Test
    void pythonReferenceUsesTheSameNumbers() throws Exception {
        JsonNode py = new ObjectMapper().readTree(new File("../defuse-data/data/policy.json"));
        var r = props.refund();
        assertThat(py.get("full_refund_max_days").asInt()).isEqualTo(r.fullMaxDays());
        assertThat(py.get("full_refund_max_sessions").asInt()).isEqualTo(r.fullMaxSessions());
        assertThat(py.get("partial_refund_max_days").asInt()).isEqualTo(r.partialMaxDays());
        assertThat(py.get("auto_refund_limit_usd").asDouble()).isEqualTo(r.autoLimitUsd());
        assertThat(py.get("goodwill_ltv_usd").asDouble()).isEqualTo(r.goodwillLtvUsd());
        assertThat(py.get("min_auto_confidence").asDouble()).isEqualTo(r.minAutoConfidence());
        py.get("plan_period_days").fields().forEachRemaining(e ->
                assertThat(r.planPeriodDays().get(e.getKey())).isEqualTo(e.getValue().asInt()));
        JsonNode ret = py.get("retention");
        var t = props.retention();
        assertThat(ret.get("discount_percent").asInt()).isEqualTo(t.discountPercent());
        assertThat(ret.get("discount_months").asInt()).isEqualTo(t.discountMonths());
        assertThat(ret.get("pause_months").asInt()).isEqualTo(t.pauseMonths());
        assertThat(ret.get("min_tenure_months").asInt()).isEqualTo(t.minTenureMonths());
        assertThat(ret.get("min_paid_renewals").asInt()).isEqualTo(t.minPaidRenewals());
        assertThat(ret.get("cooldown_days").asInt()).isEqualTo(t.cooldownDays());
        assertThat(ret.get("max_offers_per_30d").asInt()).isEqualTo(t.maxOffersPer30d());
        assertThat(ret.get("holdout_bucket").asInt()).isEqualTo(t.holdoutBucket());
        Map<String, String> offers = new HashMap<>();
        ret.get("offers").fields().forEachRemaining(e -> offers.put(e.getKey(), e.getValue().asText()));
        assertThat(offers).isEqualTo(t.offers());
        JsonNode w = py.get("risk_weights");
        var k = props.risk();
        assertThat(new double[] {w.get("base").asDouble(), w.get("dispute_signal").asDouble(), w.get("sarcasm").asDouble(),
                w.get("aggression").asDouble(), w.get("money").asDouble(), w.get("per_recent_contact").asDouble()})
                .containsExactly(k.base(), k.dispute(), k.sarcasm(), k.aggression(), k.money(), k.perContact());
        assertThat(py.get("risk_bands").get("high").asDouble()).isEqualTo(k.bandHigh());
        JsonNode money = py.get("money");
        assertThat(money.get("dispute_fee_usd").asDouble()).isEqualTo(props.money().disputeFeeUsd());
        assertThat(money.get("scheme_fee_per_dispute_usd").asDouble()).isEqualTo(props.money().schemeFeePerDisputeUsd());
    }

    @Test
    void invalidConfigRefusesToStart() {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(Bind.class)
                .withPropertyValues("defuse.policy.retention.discount-percent=150")
                .run(ctx -> assertThat(ctx).hasFailed());
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(Bind.class)
                .withPropertyValues("defuse.policy.sources.money-dispute-fee=")
                .run(ctx -> assertThat(ctx).hasFailed());
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(Bind.class)
                .run(ctx -> assertThat(ctx).hasNotFailed());
        assertThatThrownBy(() -> new PolicyProperties.Risk(.1, .4, .25, .2, .15, .1, 3, .3, .5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Autowired com.skelar.defuse.store.InMemoryStore store;
    @Autowired com.skelar.defuse.service.policy.ContextBuilderService contexts;

    @Test
    void partialRefundIsProRataNotFlatHalf() {   // T04: monthly $29.99, 20 of 30 days used -> $9.99 back
        store.reset();
        var t = store.tickets.get("T04");
        var d = policy.decide(com.skelar.defuse.service.llm.LlmClassifierService.fromGold(t), contexts.build(t.customerId), 1);
        assertThat(d.actions()).contains("refund_partial");
        assertThat(d.refundAmount()).isEqualTo(9.99);
    }
}
