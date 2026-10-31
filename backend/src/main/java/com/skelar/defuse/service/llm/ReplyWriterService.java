package com.skelar.defuse.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Customer reply. live: Gemma, given the policy decision as input; gold: templates. */
@Service
public class ReplyWriterService {
    private static final Logger log = LoggerFactory.getLogger(ReplyWriterService.class);
    private static final String SIGN = "\n\nCalmly Support";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern AMOUNT = Pattern.compile(
            "[$€£₴]\\s?\\d|\\d(?:[.,]\\d{1,2})?\\s?(?:usd|eur|gbp|uah|pln|dollars?|euros?|грн|zł)\\b", FLAGS);
    /** Outcome words in the languages we serve; each is allowed only if the matching action completed. */
    private static final Map<Pattern, Predicate<List<String>>> CLAIMS = Map.of(
            Pattern.compile("\\b(?:refunded|reembolsad|erstattet|zwrócon|повернул|повернен|повернут)", FLAGS),
            done -> done.stream().anyMatch(a -> a.startsWith("refund_")),
            Pattern.compile("\\b(?:cancell?ed|cancelad|gekündigt|storniert|anulowan|скасован|скасувал)", FLAGS),
            done -> done.contains("cancel"),
            Pattern.compile("\\b(?:paused|pausad|pausiert|wstrzyman|призупинен)", FLAGS),
            done -> done.contains("pause"),
            Pattern.compile("\\bdiscount\\s+(?:has\\s+been\\s+|was\\s+|is\\s+)?applied", FLAGS),
            done -> done.contains("apply_discount"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DAY_UK = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("uk"));

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
        var actuallyDone=executionResults.stream().filter(result -> !result.containsKey("error") && !result.containsKey("skipped")
                        && !result.containsKey("pending"))
                .map(result -> String.valueOf(result.get("action"))).toList();
        if ("suggest".equals(d.mode()) && !d.actions().isEmpty() && actuallyDone.containsAll(d.actions()))
            d=new PolicyDecision(d.actions(),"auto",d.refundAmount(),d.riskScore(),d.riskBand(),d.priority(),d.reasons());
        boolean affectsBilling = d.actions().stream().anyMatch(action -> action.startsWith("refund_")
                || action.startsWith("offer_") || java.util.Set.of("cancel", "pause", "apply_discount", "extend_access_7d").contains(action));
        boolean alreadyRefunded = d.reasons().stream().anyMatch(r -> r.startsWith("already_refunded"));
        // Financial or pending outcomes are rendered from policy and actual execution, not free-form model text.
        if (!"live".equals(mode) || !"auto".equals(d.mode()) || affectsBilling || alreadyRefunded) return template(customerName, d, ctx);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_first_name", customerName.split(" ")[0]);
        payload.put("ticket", t.text);
        payload.put("language", f.language());
        payload.put("true_goal", f.trueGoal());
        payload.put("risk_band", d.riskBand());
        payload.put("mode", d.mode());
        payload.put("actions_taken_or_pending", d.actions());
        payload.put("execution_results", executionResults);
        payload.put("actions_completed", actuallyDone);
        payload.put("actions_pending", d.actions().stream().filter(action -> !actuallyDone.contains(action)).toList());
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
            if (text == null || text.isBlank()) return template(customerName, d, ctx);
            if (unbackedClaim(text, actuallyDone)) {
                log.warn("reply writer for {} claimed an outcome that did not execute; using the template", t.id);
                return template(customerName, d, ctx);
            }
            return text;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("reply writer failed for {}: {}", t.id, e.toString());
            return template(customerName, d, ctx);
        }
    }

    /** True if model text states money or a billing outcome that actions_completed does not back. */
    static boolean unbackedClaim(String text, List<String> completed) {
        if (AMOUNT.matcher(text).find() && completed.stream().noneMatch(a -> a.startsWith("refund_"))) return true;
        return CLAIMS.entrySet().stream().anyMatch(c -> c.getKey().matcher(text).find() && !c.getValue().test(completed));
    }

    public String offerOutcome(String name, String action, boolean failed) {
        return offerOutcome(name, action, failed, "en");
    }

    public String offerOutcome(String name, String action, boolean failed, String language) {
        String first = name.split(" ")[0];
        if (uk(language)) {
            if (failed) return "Привіт, " + first + "! Дякуємо. Колега розгляне ваш запит." + SIGN;
            return "Привіт, " + first + ", " + switch (action) {
                case "apply_discount" -> String.format(Locale.US, "готово — ваша підписка знову активна, а наступні %d міс. діють зі знижкою %d%%. Раді, що ви з нами!",
                        policy.retentionDiscountMonths(), policy.retentionDiscountPercent());
                case "pause" -> String.format(Locale.US, "готово — замість скасування підписку призупинено на %d міс., списань не буде. Ваш прогрес збережено.",
                        policy.retentionPauseMonths());
                default -> "добре — ваше продовження залишається скасованим, і списань більше не буде. Нічого більше не потрібно.";
            } + SIGN;
        }
        return "Hi " + first + ", " + switch (action) {
            case "apply_discount" -> String.format(Locale.US, "done - your subscription is active again and your next %d months are %d%% off. Glad you're staying!",
                    policy.retentionDiscountMonths(), policy.retentionDiscountPercent());
            case "pause" -> String.format(Locale.US, "done - instead of ending, your subscription is paused for %d months and nothing will be charged. Your progress is saved.",
                    policy.retentionPauseMonths());
            default -> "no problem - your renewal stays canceled and you won't be charged again. Nothing else is needed.";
        } + SIGN;
    }

    public String template(String name, PolicyDecision d) {
        return template(name, d, null, "en");
    }

    public String template(String name, PolicyDecision d, CustomerContext ctx) {
        return template(name, d, ctx, "en");
    }

    /** Deterministic reply: the same facts and amounts in every language; Ukrainian is the only translation,
     *  anything else uses English. */
    public String template(String name, PolicyDecision d, CustomerContext ctx, String language) {
        boolean uk = uk(language);
        String first = name.split(" ")[0];
        List<String> acts = d.actions();
        String earlierRefund = earlierRefund(d, ctx, uk);
        if (d.mode().equals("escalate")) {
            return (uk ? "Привіт, " + first + "! Дякуємо, що звернулися. " : "Hi " + first + ", thanks for reaching out. ")
                    + (earlierRefund == null ? "" : earlierRefund + " ")
                    + (uk ? "Фахівець переглядає ваш запит і відповість протягом години." : "A specialist is reviewing your request and will reply within 1 hour.") + SIGN;
        }
        boolean pending = d.mode().equals("suggest");
        boolean offer = acts.contains("offer_discount") || acts.contains("offer_pause");
        List<String> lines = new ArrayList<>();
        if (earlierRefund != null) lines.add(earlierRefund);
        if (acts.contains("cancel")) lines.add(pending && (d.reasons().contains("low_confidence") || d.reasons().stream().anyMatch(r -> r.startsWith("autonomy_gate_")))
                ? (uk ? "Ваш запит на скасування розглядає фахівець." : "A teammate is reviewing your cancellation request.")
                : offer ? (uk ? "Ваше продовження вже зупинено — більше списань не буде." : "Your renewal is already stopped - you won't be charged again.")
                : (uk ? "Вашу підписку скасовано, і списань більше не буде." : "Your subscription is cancelled and you won't be charged again."));
        if (offer && !pending) {
            // one optional win-back with the exact policy terms; the cancel above already happened
            String terms = acts.contains("offer_discount")
                    ? (uk ? String.format(Locale.US, "Якщо бажаєте залишитися, ми можемо відновити підписку зі знижкою %d%% на наступні %d міс.%s.",
                            policy.retentionDiscountPercent(), policy.retentionDiscountMonths(), discountedPrice(ctx, true))
                          : String.format(Locale.US, "If you'd like to stay, we can restore your subscription at %d%% off for the next %d months%s.",
                            policy.retentionDiscountPercent(), policy.retentionDiscountMonths(), discountedPrice(ctx, false)))
                    : (uk ? String.format(Locale.US, "Якщо це лише тимчасово зайняті тижні, ми можемо призупинити підписку на %d міс. без оплати, і ваш прогрес збережеться.",
                            policy.retentionPauseMonths())
                          : String.format(Locale.US, "If it's just a busy stretch, we can pause your subscription for %d months instead, at no cost, and keep your progress.",
                            policy.retentionPauseMonths()));
            lines.add(terms + (uk ? " Скористайтеся кнопками під повідомленням, якщо хочете цю пропозицію. Нічого робити не потрібно, якщо не хочете."
                    : " You can use the buttons below the message if you want the offer. No action needed if you don't want it."));
        }
        if (acts.stream().anyMatch(a -> a.startsWith("refund"))) {
            if (uk) lines.add(String.format(Locale.US, "Повернення $%.2f %s.", d.refundAmount(), pending ? "очікує розгляду фахівцем" : "вже в дорозі"));
            else lines.add(String.format(Locale.US, "A refund of $%.2f %s.", d.refundAmount(), pending ? "is awaiting review by a teammate" : "is on its way"));
        }
        if (acts.contains("pause")) lines.add(uk
                ? (pending ? "Ваш запит на призупинення підписки розглядає фахівець."
                        : "Вашу підписку призупинено на " + policy.retentionPauseMonths() + " міс.; списань не буде.")
                : (pending ? "A teammate is reviewing your request to pause the subscription."
                        : "Your subscription is paused for " + policy.retentionPauseMonths() + " months; nothing will be charged meanwhile."));
        if (acts.contains("extend_access_7d")) {
            lines.add(uk ? "Ми додаємо 7 безкоштовних днів до вашого акаунту" + (pending ? " — фахівець підтверджує це зараз." : ".")
                    : "We're adding 7 free days to your account" + (pending ? " - a teammate is confirming it now." : "."));
        }
        if (acts.contains("store_refund_instructions")) {
            lines.add(uk ? "Для покупок в App Store запит на повернення можна подати на reportaproblem.apple.com. "
                    + "Для покупок у Google Play — через Google Play або зверніться до служби підтримки застосунку з номером замовлення."
                    : "For App Store purchases, request a refund at reportaproblem.apple.com. "
                    + "For Google Play purchases, request a refund through Google Play or contact the app's support team with your order ID.");
        }
        if (acts.contains("store_cancel_instructions")) {
            lines.add(uk ? "Щоб зупинити продовження: iPhone: Налаштування > Apple ID > Підписки; Android: Play Store > Платежі та підписки."
                    : "To stop renewal: iPhone Settings > Apple ID > Subscriptions, or Play Store > Payments & subscriptions on Android.");
        }
        if (acts.contains("ask_clarification")) lines.add(uk ? "Розкажіть, будь ласка, що саме не працює і на якому пристрої?"
                : "Could you tell us what exactly isn't working and on which device?");
        if (lines.isEmpty()) lines.add(uk ? "Дякуємо за повідомлення! Фахівець незабаром напише вам детальніше."
                : "Thanks for your message! A teammate will follow up with the details shortly.");
        return (uk ? "Привіт, " + first + ",\n\n" : "Hi " + first + ",\n\n") + String.join(" ", lines) + SIGN;
    }

    static boolean uk(String language) {
        return "uk".equalsIgnoreCase(language);
    }

    /** Fixture 5: report the earlier refund from the payment record; never promise a new one. */
    private static String earlierRefund(PolicyDecision d, CustomerContext ctx, boolean uk) {
        if (ctx == null || ctx.lastPayment() == null || d.reasons().stream().noneMatch(r -> r.startsWith("already_refunded"))) return null;
        var lp = ctx.lastPayment();
        if (uk) return String.format(Locale.US, "Ваш платіж на $%.2f від %s вже повернуто, тому нового повернення немає."
                + " Повернення на картку зазвичай з'являється у виписці за 5-10 робочих днів; якщо його досі немає, напишіть нам, і ми надішлемо номер повернення.",
                lp.amount(), InMemoryStore.NOW.minusDays(lp.daysAgo()).format(DAY_UK));
        return String.format(Locale.US, "Your $%.2f payment from %s has already been refunded, so there is no new refund to make."
                + " Card refunds usually take 5-10 business days to show on a statement; if you still don't see it, reply and we'll send you the refund reference.",
                lp.amount(), InMemoryStore.NOW.minusDays(lp.daysAgo()).format(DAY));
    }

    private String discountedPrice(CustomerContext ctx, boolean uk) {
        if (ctx == null || ctx.price() <= 0 || ctx.plan() == null) return "";
        String per = uk
                ? switch (ctx.plan()) { case "monthly" -> "місяць"; case "quarterly" -> "квартал"; case "annual" -> "рік"; default -> null; }
                : switch (ctx.plan()) { case "monthly" -> "month"; case "quarterly" -> "quarter"; case "annual" -> "year"; default -> null; };
        if (per == null) return "";
        double discounted = Math.round(ctx.price() * (100 - policy.retentionDiscountPercent())) / 100.0;
        return uk ? String.format(Locale.US, " ($%.2f замість $%.2f на %s; після цього повертається повна ціна)", discounted, ctx.price(), per)
                : String.format(Locale.US, " ($%.2f instead of $%.2f per %s; the full price returns after that)", discounted, ctx.price(), per);
    }
}
