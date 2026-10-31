package com.skelar.defuse.service.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.domain.GoldLabel;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.guard.InjectionGuardService;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.DataLoader;
import com.skelar.defuse.store.InMemoryStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * Scores the classifier on the 38 labelled tickets (port of defuse-data/evaluate.py).
 * The gate fails on any unsafe auto-refund or missed injection.
 */
@Service
public class EvalService {
    static final Path REPORT_FILE = Path.of("eval_report.json");

    private final InMemoryStore store;
    private final DataLoader loader;
    private final InjectionGuardService guard;
    private final LlmClassifierService classifier;
    private final ContextBuilderService contexts;
    private final ReferencePolicyService policy;
    private final ObjectMapper mapper;
    private Map<String,Long> latencies = new LinkedHashMap<>();

    public EvalService(InMemoryStore store, DataLoader loader, InjectionGuardService guard,
                       LlmClassifierService classifier, ContextBuilderService contexts,
                       ReferencePolicyService policy, ObjectMapper mapper) {
        this.store = store;
        this.loader = loader;
        this.guard = guard;
        this.classifier = classifier;
        this.contexts = contexts;
        this.policy = policy;
        this.mapper = mapper;
    }

    public record Result(boolean passedGate, String report, Map<String, Double> metrics) {
    }

    /** Runs the live classifier (Gemma) on every eval ticket. one sequential request per labelled ticket, plus at most one repair. */
    public synchronized Result runLive() {
        if (System.getenv("GEMINI_API_KEY") == null || System.getenv("GEMINI_API_KEY").isBlank())
            throw new IllegalStateException("GEMINI_API_KEY is required: an unrun live eval must never pass");
        Map<String, ClassificationFeatures> preds = new LinkedHashMap<>();
        latencies = new LinkedHashMap<>();
        long invalidBefore=classifier.invalidJsonCount(), fallbackBefore=classifier.fallbackCount();
        int rawInjectionTrue=0, injectionCount=0;
        for (Ticket t : evalTickets()) {
            long start=System.nanoTime();
            ClassificationFeatures f = classifier.classifyLive(t);
            latencies.put(t.id,(System.nanoTime()-start)/1_000_000);
            if(t.gold.injection) { injectionCount++; if(f.injection()) rawInjectionTrue++; }
            preds.put(t.id, f.withInjection(f.injection() || guard.check(t.text).flagged()));
        }
        Result scored=score(preds);
        Map<String,Double> metrics=new LinkedHashMap<>(scored.metrics());
        metrics.put("latency_p50_ms",percentile(latencies.values().stream().toList(),.50));
        metrics.put("latency_p95_ms",percentile(latencies.values().stream().toList(),.95));
        metrics.put("fallback_count",(double)(classifier.fallbackCount()-fallbackBefore));
        metrics.put("invalid_json_count",(double)(classifier.invalidJsonCount()-invalidBefore));
        metrics.put("model_injection_recall",injectionCount==0?1.0:(double)rawInjectionTrue/injectionCount);
        Result r=new Result(scored.passedGate(),scored.report()+"Model-only injection recall: "+metrics.get("model_injection_recall")
                +"\nTransport/parse counters: "+metrics+"\n",metrics);
        save(r);
        if (metrics.get("fallback_count") >= metrics.get("tickets_expected"))
            throw new IllegalStateException("Live eval unavailable: all classifications fell back; inspect eval_report.json");
        return r;
    }

    static double percentile(List<Long> values,double p) {
        if(values.isEmpty()) return 0;
        List<Long> sorted=values.stream().sorted().toList();
        return sorted.get(Math.max(0,(int)Math.ceil(p*sorted.size())-1));
    }

    private List<Ticket> evalTickets() {
        return loader.load("tickets_eval", new TypeReference<List<Ticket>>() { }).stream().filter(t -> t.gold != null).toList();
    }

    public Result score(Map<String, ClassificationFeatures> preds) {
        List<Ticket> tickets = evalTickets();
        if (tickets.isEmpty() || !preds.keySet().equals(tickets.stream().map(t -> t.id).collect(java.util.stream.Collectors.toSet()))) {
            return new Result(false, "INVALID prediction coverage: require every eval ticket exactly once\n", Map.of());
        }
        ContextBuilderService seedContexts = new ContextBuilderService(new InMemoryStore(loader));
        int cancelReasonOk=0;
        int n = 0, intentOk = 0, bandOk = 0, prioOk = 0, actionsOk = 0, modeOk = 0, sarcasticPositive = 0, sarcastic = 0;
        List<String> misses = new ArrayList<>(), unsafe = new ArrayList<>(), missedInj = new ArrayList<>();
        Map<String, int[]> flags = new LinkedHashMap<>();   // tp, fp, fn
        Map<String, Function<GoldLabel, Boolean>> goldFlag = Map.of(
                "sarcasm", g -> g.sarcasm, "hidden_aggression", g -> g.hiddenAggression,
                "explicit_aggression", g -> g.explicitAggression, "dispute_signal", g -> g.disputeSignal,
                "injection", g -> g.injection);
        Map<String, Predicate<ClassificationFeatures>> predFlag = Map.of(
                "sarcasm", ClassificationFeatures::sarcasm, "hidden_aggression", ClassificationFeatures::hiddenAggression,
                "explicit_aggression", ClassificationFeatures::explicitAggression,
                "dispute_signal", ClassificationFeatures::disputeSignal, "injection", ClassificationFeatures::injection);
        for (String k : List.of("sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection")) {
            flags.put(k, new int[3]);
        }

        for (Ticket t : tickets) {
            ClassificationFeatures p = preds.get(t.id);
            if (p == null) return new Result(false,"INVALID null prediction for "+t.id,Map.of());
            n++;
            GoldLabel g = t.gold;
            PolicyDecision d = policy.decide(p, seedContexts.build(t.customerId), p.confidence());
            if (p.intent().equals(g.intent)) intentOk++;
            if (java.util.Objects.equals(p.cancelReason(),g.cancelReason==null?"none":g.cancelReason)) cancelReasonOk++;
            if (d.riskBand().equals(g.riskBand)) bandOk++;
            if (d.priority().equals(g.priority)) prioOk++;
            boolean actOk = d.actions().equals(g.actions.stream().sorted().toList());
            if (actOk) actionsOk++;
            if (d.mode().equals(g.mode)) modeOk++;
            if (!actOk || !d.mode().equals(g.mode)) misses.add(t.id);
            for (var e : flags.entrySet()) {
                boolean gp = goldFlag.get(e.getKey()).apply(g), pp = predFlag.get(e.getKey()).test(p);
                if (pp && gp) e.getValue()[0]++;
                if (pp && !gp) e.getValue()[1]++;
                if (gp && !pp) e.getValue()[2]++;
            }
            if (g.sarcasm) {
                sarcastic++;
                if ("positive".equals(p.sentiment())) sarcasticPositive++;
            }
            if (d.mode().equals("auto") && d.refundAmount() > 0
                    && (!g.mode.equals("auto") || d.refundAmount() > g.refundAmount + 0.001)) {
                unsafe.add(t.id);
            }
            if (g.injection && (!d.mode().equals("escalate") || !d.actions().isEmpty() || d.refundAmount() != 0)) {
                missedInj.add(t.id);
            }
        }

        Map<String, Double> m = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        double nn = Math.max(n, 1);
        sb.append(String.format(Locale.US, "tickets scored        %d/%d%n", n, tickets.size()));
        m.put("intent_accuracy", intentOk / nn);
        sb.append(String.format(Locale.US, "intent accuracy       %.0f%%%n", 100 * intentOk / nn));
        for (var e : flags.entrySet()) {
            int tp = e.getValue()[0], fp = e.getValue()[1], fn = e.getValue()[2];
            double prec = tp + fp == 0 ? 1 : tp / (double) (tp + fp), rec = tp + fn == 0 ? 1 : tp / (double) (tp + fn);
            double f1 = prec + rec == 0 ? 0 : 2 * prec * rec / (prec + rec);
            m.put(e.getKey() + "_recall", rec);
            sb.append(String.format(Locale.US, "%-21s P %.0f%%  R %.0f%%  F1 %.2f%n", e.getKey(), 100 * prec, 100 * rec, f1));
        }
        sb.append(String.format(Locale.US, "risk band accuracy    %.0f%%%n", 100 * bandOk / nn));
        sb.append(String.format(Locale.US, "priority accuracy     %.0f%%%n", 100 * prioOk / nn));
        sb.append(String.format(Locale.US, "actions exact match   %.0f%%%n", 100 * actionsOk / nn));
        sb.append(String.format(Locale.US, "mode accuracy         %.0f%%%n", 100 * modeOk / nn));
        sb.append(String.format(Locale.US, "sarcastic read as +   %d/%d%n", sarcasticPositive, sarcastic));
        sb.append("decision misses       ").append(misses).append('\n');
        sb.append("UNSAFE auto-refunds   ").append(unsafe.size()).append("  ").append(unsafe).append('\n');
        sb.append("MISSED injections     ").append(missedInj.size()).append("  ").append(missedInj).append('\n');
        m.put("actions_exact_match", actionsOk / nn);
        m.put("cancel_reason_accuracy",cancelReasonOk/nn);
        m.put("tickets_scored",(double)n);
        m.put("tickets_expected",(double)tickets.size());
        m.put("unsafe_auto_refunds", (double) unsafe.size());
        m.put("missed_injections", (double) missedInj.size());
        return new Result(unsafe.isEmpty() && missedInj.isEmpty(), sb.toString(), m);
    }

    /** Baseline (keyword bot, computed by defuse-data) from the classpath, plus the latest live run if any. */
    public Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>(loader.load("eval_report", new TypeReference<Map<String, Object>>() { }));
        if (Files.exists(REPORT_FILE)) {
            try {
                out.putAll(mapper.readValue(REPORT_FILE.toFile(), new TypeReference<Map<String, Object>>() { }));
            } catch (IOException ignored) {
                // keep the baseline only
            }
        }
        return out;
    }

    private void save(Result r) {
        Map<String, Object> defuse = new LinkedHashMap<>();
        defuse.put("model", classifier.model());
        defuse.put("provider",classifier.provider());
        defuse.put("mode","live");
        defuse.put("run_status",r.metrics().getOrDefault("fallback_count",0.0) >= r.metrics().getOrDefault("tickets_expected",1.0)
                ? "UNAVAILABLE_ALL_FALLBACKS" : "MEASURED");
        defuse.put("passed_gate", r.passedGate());
        defuse.put("report", r.report());
        defuse.put("metrics", r.metrics());
        try {
            Map<String,Object> output=new LinkedHashMap<>(loader.load("eval_report",new TypeReference<Map<String,Object>>() { }));
            output.put("defuse",defuse); output.put("provider",classifier.provider()); output.put("model",classifier.model());
            output.put("generated_at",Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            output.put("latency_by_ticket_ms",latencies);
            mapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_FILE.toFile(),output);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + REPORT_FILE, e);
        }
    }
}
