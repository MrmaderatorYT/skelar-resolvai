package com.skelar.defuse.model.dto;

/** amountAtRisk + feesAtRisk = gross exposure if this becomes a chargeback. disputeFeeScenario = pDispute x feesAtRisk
 *  is an illustrative scenario from the synthetic baseline, not a measured or avoided outcome (spec K). */
public record MoneyAtRisk(double amountAtRisk, double feesAtRisk, double pDispute, double disputeFeeScenario) {
}
