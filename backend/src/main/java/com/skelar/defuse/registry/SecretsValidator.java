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
                boolean connectorSetting = key.startsWith("defuse.datasources.") || key.startsWith("defuse.billing[") || key.startsWith("defuse.billing.");
                boolean datasourceSetting = key.startsWith("spring.datasource.");
                if (!connectorSetting && !datasourceSetting) continue;
                if (datasourceSetting && bundledDefaults(source.getName())) continue;
                Object value = raw.getProperty(key);
                if (value == null) continue;
                if (datasourceSetting && isCredential(key) && !ENV.matcher(value.toString()).matches())
                    throw new IllegalStateException("Datasource credentials must use environment placeholders: " + key);
                validateSetting(key, value.toString());
            }
    }
    /**
     * Only the bundled classpath application.yml may hold the literal H2 demo defaults. The test is on the
     * source's classpath location, not a file name, so a ./config/application.yml on disk is still checked.
     */
    private static boolean bundledDefaults(String sourceName) {
        return sourceName.contains("application.yml") && (sourceName.contains("classpath:/") || sourceName.contains("class path resource [application.yml]"));
    }
    /** Covers spring.datasource.url / username / password and the same keys under spring.datasource.hikari.*. */
    private static boolean isCredential(String key) {
        return key.endsWith("url") || key.endsWith("username") || key.endsWith("password");
    }
    public static void validateSetting(String key, String raw) {
        boolean placeholder = ENV.matcher(raw).matches();
        if ((key.toLowerCase().contains("password") && !placeholder)
                || (DSN.matcher(raw).find() && !placeholder))
            throw new IllegalStateException("Secret must be supplied as an environment placeholder: " + key);
    }
}
