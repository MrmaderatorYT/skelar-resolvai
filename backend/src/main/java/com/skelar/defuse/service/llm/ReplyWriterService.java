package com.skelar.defuse.service.llm;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
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

/** Customer reply. live: Claude Sonnet, given the policy decision as input; gold: templates. */
@Service
public class ReplyWriterService {
    private static final Logger log = LoggerFactory.getLogger(ReplyWriterService.class);
    private static final String SIGN = "\n\nCalmly Support";

    private final AnthropicClientHolder clients;
    private final ObjectMapper mapper;
    private final String mode;
    private final String model;

    public ReplyWriterService(AnthropicClientHolder clients, ObjectMapper mapper,
                              @Value("${defuse.llm.mode}") String mode,
                              @Value("${defuse.llm.reply-model}") String model) {
        this.clients = clients;
        this.mapper = mapper;
        this.mode = mode;
        this.model = model;
    }

    public String write(Ticket t, ClassificationFeatures f, CustomerContext ctx, PolicyDecision d, String customerName) {
        if (!"live".equals(mode)) return template(customerName, d);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer_first_name", customerName.split(" ")[0]);
        payload.put("ticket", t.text);
        payload.put("language", f.language());
        payload.put("true_goal", f.trueGoal());
        payload.put("risk_band", d.riskBand());
        payload.put("mode", d.mode());
        payload.put("actions_taken_or_pending", d.actions());
        payload.put("refund_amount_usd", d.refundAmount());
        payload.put("reasons", d.reasons());
        payload.put("plan", ctx.plan());
        payload.put("platform", ctx.platform());
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2000L)
                    .system(Prompts.REPLY_SYSTEM)
                    .addUserMessage(mapper.writeValueAsString(payload))
                    .build();
            String text = clients.get().messages().create(params).content().stream()
                    .flatMap(cb -> cb.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            return text.isBlank() ? template(customerName, d) : text;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("reply writer failed for {}: {}", t.id, e.toString());
            return template(customerName, d);
        }
    }

    public static String template(String name, PolicyDecision d) {
        String first = name.split(" ")[0];
        List<String> acts = d.actions();
        if (d.mode().equals("escalate")) {
            return "Hi " + first + ", thanks for reaching out. A specialist is reviewing your request and will reply within 1 hour." + SIGN;
        }
        boolean pending = d.mode().equals("suggest");
        List<String> lines = new ArrayList<>();
        if (acts.contains("cancel")) lines.add("Your subscription is cancelled and you won't be charged again.");
        if (acts.stream().anyMatch(a -> a.startsWith("refund"))) {
            String state = pending ? "is being confirmed by a teammate (within 1 hour)" : "is on its way";
            lines.add(String.format(Locale.US, "A refund of $%.2f %s; card refunds land in 5-10 business days.", d.refundAmount(), state));
        }
        if (acts.contains("pause")) lines.add("Your subscription is paused for 2 months; nothing will be charged meanwhile.");
        if (acts.contains("extend_access_7d")) {
            lines.add("We're adding 7 free days to your account" + (pending ? " - a teammate is confirming it now." : "."));
        }
        if (acts.contains("store_refund_instructions")) {
            lines.add("Purchases made through Apple or Google can only be refunded by them: "
                    + "Apple - reportaproblem.apple.com; Google - Play Store > Payments & subscriptions.");
        }
        if (acts.contains("store_cancel_instructions")) {
            lines.add("To stop renewal: iPhone Settings > Apple ID > Subscriptions, or Play Store > Payments & subscriptions on Android.");
        }
        if (acts.contains("ask_clarification")) lines.add("Could you tell us what exactly isn't working and on which device?");
        if (lines.isEmpty()) lines.add("Thanks for your message! A teammate will follow up with the details shortly.");
        return "Hi " + first + ", " + String.join(" ", lines) + SIGN;
    }
}
