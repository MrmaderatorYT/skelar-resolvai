package com.skelar.defuse.model.dto;

/** amountAtRisk + feesAtRisk = gross exposure if this becomes a chargeback. A refund does not save the
 *  principal; what an early resolution avoids is the expected fees: pDispute x feesAtRisk. */
public record MoneyAtRisk(double amountAtRisk, double feesAtRisk, double pDispute, double expectedFeesAvoided) {
}
