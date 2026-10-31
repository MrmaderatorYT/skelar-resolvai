package com.skelar.defuse.registry;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
/** https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html */
@Validated
@ConfigurationProperties(prefix = "defuse")
public class PluginProperties {
    @NotEmpty private Map<String, Map<String, String>> datasources = new LinkedHashMap<>();
    @NotEmpty private List<Map<String, String>> billing = List.of();
    public Map<String, Map<String, String>> getDatasources() { return datasources; }
    public void setDatasources(Map<String, Map<String, String>> value) { datasources = value; }
    public List<Map<String, String>> getBilling() { return billing; }
    public void setBilling(List<Map<String, String>> value) { billing = value; }
}
