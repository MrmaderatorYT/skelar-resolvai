package com.skelar.defuse.model.dto;

import java.util.List;

/** Output of ReferencePolicyService: the only thing allowed to move money. */
public record PolicyDecision(
        List<String> actions,
        String mode,            // auto | suggest | escalate
        double refundAmount,
        double riskScore,
        String riskBand,        // low | medium | high
        String priority,        // P1..P4
        List<String> reasons) {
}
