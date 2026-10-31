package com.skelar.defuse.config.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Which policy revision decided: defuse.policy-version if set, else a content hash of the loaded policy,
 *  so a reviewer's approval and every audit event name the exact rules in force. */
@Component
public class PolicyVersion {
    private final String version;

    public PolicyVersion(PolicyProperties props, @Value("${defuse.policy-version:}") String configured) {
        this.version = configured != null && !configured.isBlank() ? configured.trim() : sha256(props.toString()).substring(0, 12);
    }

    public String get() {
        return version;
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
