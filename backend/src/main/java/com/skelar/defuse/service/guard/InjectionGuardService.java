package com.skelar.defuse.service.guard;

import com.skelar.defuse.model.dto.InjectionCheck;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Regex prompt-injection guard. Runs before the LLM; regex OR model flag escalates the ticket. */
@Service
public class InjectionGuardService {
    static final List<String> PATTERNS = List.of(
            "ignore (all )?(the )?(previous|prior|above) (instructions|rules|prompts?)",
            "you are now (in )?(an? )?(admin|developer|system|god) mode",
            "<!--.*?-->",
            "(^|\\W)system\\s*:",
            "\\[\\s*system\\s*\\]",
            "(policy|policies|limits?|rules?) (has been |have been |were |was )?(updated|changed|overridden|override)",
            "auto-?approved?",
            "do not (tell|notify|inform|alert) (the )?(operator|agent|human|staff|team)",
            "системн\\w* повідомлення",
            "ігноруй\\w* (всі |усі )?(попередні )?(інструкції|правила)",
            "не повідомляй\\w* (оператор|агент|людин)");

    private static final List<Pattern> COMPILED = PATTERNS.stream()
            .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL
                    | Pattern.UNICODE_CHARACTER_CLASS))
            .toList();

    public InjectionCheck check(String text) {
        List<String> hits = COMPILED.stream().filter(p -> p.matcher(text).find()).map(Pattern::pattern).toList();
        return new InjectionCheck(!hits.isEmpty(), hits);
    }
}
