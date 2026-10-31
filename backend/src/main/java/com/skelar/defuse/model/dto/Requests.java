package com.skelar.defuse.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Requests {
    private Requests() {
    }

    public record NewTicket(@NotBlank String customerId, @NotBlank @Size(max = 5000) String text, String channel) {
    }

    /** actionHash, when sent, must equal the hash the reviewer saw; the server recomputes it from fresh state. */
    public record Approve(@NotBlank String analysisId, @Size(max = 2000) String replyText, @Size(max = 128) String actionHash) {
    }

    public record HumanReply(@NotBlank @Size(max = 2000) String text) {}

    public record Reject(@NotBlank @Size(max = 500) String reason) {
    }
}
