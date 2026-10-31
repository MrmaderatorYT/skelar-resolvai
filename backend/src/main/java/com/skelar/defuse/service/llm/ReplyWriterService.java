package com.skelar.defuse.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Customer reply. live: Gemma, given the policy decision as input; gold: templates. */
@Service
public class ReplyWriterService {
    private static final Logger log = LoggerFactory.getLogger(ReplyWriterService.class);
    private static final String SIGN = "\n\nCalmly Support";

    private final LlmClient client;
    private final ReferencePolicyService policy;
    private final ObjectMapper mapper;
    private final String mode;

    public ReplyWriterService(LlmClient client, ObjectMapper mapper, LlmSettings settings, ReferencePolicyService policy) {
        this.client = client; this.mapper = mapper; this.mode = settings.mode(); this.policy = policy;
    }

    public String write(Ticket t, ClassificationFeatures f, CustomerContext ctx, PolicyDecision d, String name) {
        return write(t,f,ctx,d,name,List.of());
    }

    public String write(Ticket t, ClassificationFeatures f, CustomerContext ctx, PolicyDecision d, String customerName,
                        List<Map<String,Object>> executionResults) {
        var actuallyDone=executionResults.stream().filter(result -> !result.containsKey("error") && !result.containsKey("skipped"))
                .map(result -> String.valueOf(result.get("action"))).toList();
        if ("suggest".equals(d.mode()) && !d.actions().isEmpty() && actuallyDone.containsAll(d.actions()))
            d=new PolicyDecision(d.actions(),"auto",d.refundAmount(),d.riskScore(),d.riskBand(),d.priority(),d.reasons());
        boolean affectsBilling = d.actions().stream().anyMatch(action -> action.startsWith("refund_")
                || action.startsWith("offer_") || java.util.Set.of("cancel", "pause", "apply_discount", "extend_access_7d").contains(action));
        // Financial or pending outcomes are rendered from policy and actual execution, not free-form model text.
        if (!"live".equals(mode) || !"auto".equals(d.mode()) || affectsBilling) return template(customerName, d);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_first_name", customerName.split(" ")[0]);
        payload.put("ticket", t.text);
        payload.put("language", f.language());
        payload.put("true_goal", f.trueGoal());
        payload.put("risk_band", d.riskBand());
        payload.put("mode", d.mode());
        payload.put("actions_taken_or_pending", d.actions());
        payload.put("execution_results", executionResults);
        var completed = executionResults.stream().filter(result -> !result.containsKey("error") && !result.containsKey("skipped"))
                .map(result -> String.valueOf(result.get("action"))).toList();
        payload.put("actions_completed", completed);
        payload.put("actions_pending", d.actions().stream().filter(action -> !completed.contains(action)).toList());
        payload.put("offer_discount_percent", policy.retentionDiscountPercent());
        payload.put("offer_discount_months", policy.retentionDiscountMonths());
        payload.put("offer_pause_months", policy.retentionPauseMonths());
        payload.put("refund_amount_usd", d.refundAmount());
        payload.put("reasons", d.reasons());
        payload.put("plan", ctx.plan());
        payload.put("platform", ctx.platform());
        try {
            String text = client.generateText(Prompts.REPLY_SYSTEM + "\nUNTRUSTED_TICKET_AND_VERIFIED_DECISION_JSON:\n"
                    + mapper.writeValueAsString(payload).replace("<", "\\u003c").replace(">", "\\u003e"));
            return text.isBlank() ? template(customerName, d) : text;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("reply writer failed for {}: {}", t.id, e.toString());
            return template(customerName, d);
        }
    }

    public String offerOutcome(String name, String action, boolean failed) {
        String first = name.split(" ")[0];
        if (failed) return "Hi " + first + ", thanks! A teammate will review your request." + SIGN;
        return "Hi " + first + ", " + switch (action) {
            case "apply_discount" -> String.format(Locale.US, "done - your next %d months are %d%% off. Glad you're staying!",
                    policy.retentionDiscountMonths(), policy.retentionDiscountPercent());
            case "pause" -> String.format(Locale.US, "done - your subscription is paused for %d months and nothing will be charged. Your progress is saved.",
                    policy.retentionPauseMonths());
            default -> "your subscription is cancelled and you won't be charged again.";
        } + SIGN;
    }

    public String template(String name, PolicyDecision d) {
        String first = name.split(" ")[0];
        List<String> acts = d.actions();
        if (d.mode().equals("escalate")) {
            return "Hi " + first + ", thanks for reaching out. A specialist is reviewing your request and will reply within 1 hour." + SIGN;
        }
        boolean pending = d.mode().equals("suggest");
        List<String> lines = new ArrayList<>();
        String canStillCancel = " If you'd still like to cancel, just reply CANCEL and we'll do it right away - no questions asked.";
        if (acts.contains("offer_discount")) {
            lines.add(String.format(Locale.US, "Before you go: we can take %d%% off your next %d months.",
                    policy.retentionDiscountPercent(), policy.retentionDiscountMonths())
                    + " Reply YES to keep Calmly at the lower price." + canStillCancel);
        }
        if (acts.contains("offer_pause")) {
            lines.add(String.format(Locale.US, "If it's just a busy stretch, we can pause your subscription for %d months at no cost and keep your progress.",
                    policy.retentionPauseMonths()) + " Reply PAUSE to do that." + canStillCancel);
        }
        if (acts.contains("cancel")) lines.add(pending && (d.reasons().contains("low_confidence") || d.reasons().stream().anyMatch(r -> r.startsWith("autonomy_gate_")))
                ? "A teammate is reviewing your cancellation request." : "Your subscription is cancelled and you won't be charged again.");
        if (acts.stream().anyMatch(a -> a.startsWith("refund"))) {
            String state = pending ? "is awaiting review by a teammate" : "is on its way";
            lines.add(String.format(Locale.US, "A refund of $%.2f %s.", d.refundAmount(), state));
        }
        if (acts.contains("pause")) lines.add(pending
                ? "A teammate is reviewing your request to pause the subscription."
                : "Your subscription is paused for " + policy.retentionPauseMonths() + " months; nothing will be charged meanwhile.");
        if (acts.contains("extend_access_7d")) {
            lines.add("We're adding 7 free days to your account" + (pending ? " - a teammate is confirming it now." : "."));
        }
        if (acts.contains("store_refund_instructions")) {
            lines.add("For App Store purchases, request a refund at reportaproblem.apple.com. "
                    + "For Google Play purchases, request a refund through Google Play or contact the app's support team with your order ID.");
        }
        if (acts.contains("store_cancel_instructions")) {
            lines.add("To stop renewal: iPhone Settings > Apple ID > Subscriptions, or Play Store > Payments & subscriptions on Android.");
        }
        if (acts.contains("ask_clarification")) lines.add("Could you tell us what exactly isn't working and on which device?");
        if (lines.isEmpty()) lines.add("Thanks for your message! A teammate will follow up with the details shortly.");
        return "Hi " + first + ",\n\n" + String.join(" ", lines) + SIGN;
    }
}
