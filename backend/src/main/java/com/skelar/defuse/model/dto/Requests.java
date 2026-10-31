package com.skelar.defuse.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Requests {
    private Requests() {
    }

    public record NewTicket(@NotBlank String customerId, @NotBlank @Size(max = 5000) String text, String channel) {
    }

    public record Approve(@Size(max = 2000) String replyText) {
    }

    public record Reject(@Size(max = 500) String reason) {
    }
}
