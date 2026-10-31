package com.skelar.defuse.registry;
import java.util.regex.Pattern;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Component;
/** Inspect raw property sources, before placeholder expansion; never print secret values. */
@Component
public class SecretsValidator {
    private static final Pattern ENV = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}");
    private static final Pattern DSN = Pattern.compile("(?i)(://[^/\\s:@]+:[^@\\s/]+@|(?:password|pwd)\\s*=\\s*[^\\s;]+)");
    public SecretsValidator(ConfigurableEnvironment environment) {
        for (var source : environment.getPropertySources()) if (source instanceof EnumerablePropertySource<?> raw)
            for (String key : raw.getPropertyNames()) {
                if (!key.startsWith("defuse.datasources.") && !key.startsWith("defuse.billing[") && !key.startsWith("defuse.billing.")) continue;
                Object value = raw.getProperty(key);
                if (value != null) validateSetting(key, value.toString());
            }
    }
    public static void validateSetting(String key, String raw) {
        boolean placeholder = ENV.matcher(raw).matches();
        if ((key.toLowerCase().contains("password") && !placeholder)
                || (DSN.matcher(raw).find() && !placeholder))
            throw new IllegalStateException("Secret must be supplied as an environment placeholder: " + key);
    }
}
