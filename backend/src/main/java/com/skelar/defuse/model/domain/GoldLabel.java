package com.skelar.defuse.model.domain;

import java.util.List;

/** Hand-made eval labels. Used in gold mode and tests; never sent to the frontend. */
public class GoldLabel {
    public String intent;
    public String cancelReason;
    public boolean sarcasm;
    public boolean hiddenAggression;
    public boolean explicitAggression;
    public boolean disputeSignal;
    public boolean injection;
    public String riskBand;
    public List<String> actions;
    public String mode;
    public double refundAmount;
    public String priority;
    public String trueGoal;
    public List<String> tags;
}
