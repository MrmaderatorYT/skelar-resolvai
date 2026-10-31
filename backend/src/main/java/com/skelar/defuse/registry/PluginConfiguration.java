package com.skelar.defuse.registry;
import com.skelar.defuse.plugins.billing.StoreBillingProvider;
import com.skelar.defuse.spi.BillingProvider;
import com.skelar.defuse.store.InMemoryStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** Spring beans are the plugin lifecycle; dynamic jar loading is unnecessary for this MVP. */
@Configuration
@EnableConfigurationProperties(PluginProperties.class)
public class PluginConfiguration {
    @Bean BillingProvider appStoreProvider(InMemoryStore store) { return new StoreBillingProvider("app_store",store); }
    @Bean BillingProvider googlePlayProvider(InMemoryStore store) { return new StoreBillingProvider("google_play",store); }
}
