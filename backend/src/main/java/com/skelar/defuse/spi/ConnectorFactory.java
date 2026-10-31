package com.skelar.defuse.spi;
import java.util.Map;
public interface ConnectorFactory<T> {
    String type();
    Class<T> kind();
    T create(Map<String, String> settings);
}
