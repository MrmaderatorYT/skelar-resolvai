package com.skelar.defuse;

import static org.assertj.core.api.Assertions.*;
import com.skelar.defuse.registry.SecretsValidator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class SecretsValidatorTest {
    private static StandardEnvironment env(String sourceName, Map<String, Object> properties, String... profiles) {
        var env = new StandardEnvironment();
        env.setActiveProfiles(profiles);
        env.getPropertySources().addFirst(new MapPropertySource(sourceName, properties));
        return env;
    }
    @Test void rejectsLiteralPasswordInExternalConfigFile() {
        var env = env("applicationConfig: [file:./config/application.yml]", Map.of("spring.datasource.password", "hunter2"), "postgres");
        assertThatThrownBy(() -> new SecretsValidator(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }
    @Test void rejectsLiteralDsnOutsidePostgresProfile() {
        var env = env("commandLineArgs", Map.of("spring.datasource.url", "jdbc:postgresql://app:hunter2@db/defuse"));
        assertThatThrownBy(() -> new SecretsValidator(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsLiteralHikariPassword() {
        var env = env("commandLineArgs", Map.of("spring.datasource.hikari.password", "hunter2"), "postgres");
        assertThatThrownBy(() -> new SecretsValidator(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.hikari.password");
    }
    @Test void acceptsBundledH2DefaultsAndEnvironmentPlaceholders() {
        var bundled = env("applicationConfig: [classpath:/application.yml]",
                Map.of("spring.datasource.url", "jdbc:h2:mem:defuse;MODE=PostgreSQL", "spring.datasource.password", "${DEFUSE_H2_PASSWORD:}"), "postgres");
        assertThatCode(() -> new SecretsValidator(bundled)).doesNotThrowAnyException();
        var bootName = env("Config resource 'class path resource [application.yml]' via location 'optional:classpath:/'",
                Map.of("spring.datasource.url", "jdbc:h2:mem:defuse;MODE=PostgreSQL"));
        assertThatCode(() -> new SecretsValidator(bootName)).doesNotThrowAnyException();
        var postgres = env("applicationConfig: [classpath:/application-postgres.yml]",
                Map.of("spring.datasource.password", "${DEFUSE_DB_PASSWORD}"), "postgres");
        assertThatCode(() -> new SecretsValidator(postgres)).doesNotThrowAnyException();
    }
}
