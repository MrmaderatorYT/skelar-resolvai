package com.skelar.defuse.service.analytics;

import com.skelar.defuse.config.policy.PolicyProperties;
import com.skelar.defuse.model.domain.HistoryTicket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.MoneyAtRisk;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Money at risk per ticket, priced with dispute rates from the (synthetic) history baseline. Scenarios, not savings. */
@Service
public class MoneyService {
    private final InMemoryStore store;
    private final PolicyProperties.Money money;

    public MoneyService(InMemoryStore store, PolicyProperties props) {
        this.store = store;
        this.money = props.money();
    }

    public PolicyProperties.Money settings() {
        return money;
    }

    public Map<String, Double> pDisputeByBand() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String band : List.of("low", "medium", "high")) {
            List<HistoryTicket> rows = store.history.stream().filter(t -> t.riskBand.equals(band)).toList();
            double rate = rows.isEmpty() ? 0 : rows.stream().filter(t -> t.chargebackFiledAfter).count() / (double) rows.size();
            out.put(band, Math.round(rate * 1000.0) / 1000.0);
        }
        return out;
    }

    public MoneyAtRisk atRisk(ClassificationFeatures f, PolicyDecision d, CustomerContext ctx) {
        boolean atRisk = ReferencePolicyService.MONEY_INTENTS.contains(f.intent()) && "card".equals(ctx.billingProcessor())
                && ctx.lastPayment().status().equals("succeeded");
        double amount = atRisk ? ctx.lastPayment().amount() : 0;
        double fees = atRisk ? money.feesPerDispute() : 0;
        double p = pDisputeByBand().get(d.riskBand());
        return new MoneyAtRisk(amount, fees, p, Math.round(p * fees * 100.0) / 100.0);
    }
}
