package com.skelar.defuse.service.guard;

import com.skelar.defuse.model.dto.InjectionCheck;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Regex prompt-injection guard. Runs before the LLM; regex OR model flag escalates the ticket. */
@Service
public class InjectionGuardService {
    static final List<String> PATTERNS = List.of(
            "</ticket\\s*>",
            "<<\\s*sys\\s*>>",
            "(pretend|act as|role.?play).{0,35}(admin|developer|system)",
            "du bist jetzt.{0,25}(admin|administrator)",
            "actúa como.{0,25}(admin|administrador)",
            "jesteś teraz.{0,25}(admin|administratorem)",
            "(ігноруй|ігноруйте).{0,30}(інструкції|правила)",
            "(політик|ліміт).{0,35}(оновлен|підвищен|змінен)",
            "(zasady|limit).{0,30}(zmienione|podniesiony|zwiększony)",
            "(don't|do not).{0,12}(tell|inform|notify).{0,15}(operator|human|agent)",
            "nicht.{0,20}(operator|mitarbeiter).{0,20}informieren",
            "nie informuj.{0,20}operator",
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

    /** OWASP LLM01:2025. This is bounded heuristic detection, never an authorization boundary. */
    public InjectionCheck check(String text) {
        if (text == null || text.length() > 5000) return new InjectionCheck(true,List.of("invalid_ticket_length"));
        List<String> variants=new ArrayList<>();variants.add(text);variants.add(canonical(text));variants.add(rot13(text));
        var encoded=Pattern.compile("[A-Za-z0-9+/]{24,}={0,2}").matcher(text);
        int decoded=0;
        while(encoded.find() && decoded++ < 8) try {
            String value=new String(Base64.getDecoder().decode(encoded.group()),StandardCharsets.UTF_8);
            variants.add(value);variants.add(canonical(value));
        } catch(IllegalArgumentException ignored) { /* Not a base64 token. */ }
        var hits=new LinkedHashSet<String>();
        for(Pattern p:COMPILED) if(variants.stream().anyMatch(value->p.matcher(value).find())) hits.add(p.pattern());
        return new InjectionCheck(!hits.isEmpty(),List.copyOf(hits));
    }
    private static String canonical(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\p{Cf}","")
                .replace('о','o').replace('О','O').replace('а','a').replace('А','A')
                .replace('е','e').replace('Е','E').replace('і','i').replace('І','I')
                .replace('р','p').replace('Р','P').replace('с','c').replace('С','C');
    }
    private static String rot13(String text) {
        StringBuilder out=new StringBuilder(text.length());
        for(char c:text.toCharArray()) out.append(c>='a' && c<='z'?(char)('a'+(c-'a'+13)%26)
                :c>='A' && c<='Z'?(char)('A'+(c-'A'+13)%26):c);
        return out.toString();
    }
}
