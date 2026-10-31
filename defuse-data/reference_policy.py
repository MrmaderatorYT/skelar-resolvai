"""Reference policy engine for Defuse.

The LLM extracts features (intent, sarcasm, aggression, dispute signal, injection).
Everything below is deterministic code: the model can propose, only this decides.
"""
import json
import math
import os
from datetime import datetime, timezone
from pathlib import Path

NOW = datetime(2026, 10, 31, 12, 0, tzinfo=timezone.utc)
DATA = Path(os.environ.get("DEFUSE_DATA_DIR", Path(__file__).parent / "data"))

POLICY = {
    "auto_refund_limit_usd": 60.0,
    "full_refund_max_days": 14,
    "full_refund_max_sessions": 2,
    "partial_refund_max_days": 30,
    "partial_refund_share": 0.5,
    "goodwill_ltv_usd": 100.0,
    "min_auto_confidence": 0.85,
    "risk_weights": {
        "base": 0.10,
        "dispute_signal": 0.40,
        "sarcasm": 0.25,
        "aggression": 0.20,
        "money": 0.15,
        "per_recent_contact": 0.10,
        "max_contacts": 3,
    },
    "risk_bands": {"high": 0.50, "medium": 0.30},
}

REFUND_INTENTS = {"refund", "cancel_refund"}
CANCEL_INTENTS = {"cancel", "cancel_refund"}
MONEY_INTENTS = REFUND_INTENTS | {"duplicate_charge"}
INTENTS = sorted(MONEY_INTENTS | CANCEL_INTENTS | {
    "pause", "technical_issue", "account_access", "account_security",
    "question", "billing_question", "unclear",
})


def load(name):
    return json.loads((DATA / f"{name}.json").read_text())


def load_all():
    return {k: load(k) for k in ("customers", "subscriptions", "payments", "usage")}


def _days_ago(iso):
    return (NOW - datetime.fromisoformat(iso)).days


def build_context(customer_id, data=None):
    """What the agent's read-only tools return for one customer."""
    data = load_all() if data is None else data
    cust = next(c for c in data["customers"] if c["id"] == customer_id)
    sub = next(s for s in data["subscriptions"] if s["customer_id"] == customer_id)
    usage = next(u for u in data["usage"] if u["customer_id"] == customer_id)
    pays = sorted((p for p in data["payments"] if p["customer_id"] == customer_id),
                  key=lambda p: p["created_at"])
    if not pays:
        raise ValueError("no_payments: human review required")
    last = pays[-1]
    same_day = [p for p in pays if p["status"] == "succeeded" and p["amount"] == last["amount"]
                and p["created_at"][:10] == last["created_at"][:10]]
    return {
        "customer_id": customer_id,
        "platform": cust["platform"],
        "billing_processor": sub["processor"],
        "ltv_usd": cust["ltv_usd"],
        "contacts_last_30d": cust["contacts_last_30d"],
        "refunds_last_180d": sum(1 for p in pays if p["status"] == "refunded"
                                 and _days_ago(p["created_at"]) <= 180),
        "chargebacks_lifetime": sum(1 for p in pays if p["status"] == "charged_back"),
        "subscription_status": sub["status"],
        "plan": sub["plan"],
        "price": sub["price"],
        "last_payment": {"id": last["id"], "amount": last["amount"], "status": last["status"],
                         "days_ago": _days_ago(last["created_at"])},
        "duplicate_payment_id": same_day[-1]["id"] if len(same_day) > 1 else None,
        "sessions_since_last_charge": usage["sessions_since_last_charge"],
        "cancel_flow_abandoned": usage["cancel_flow_abandoned"],
    }


def risk_score(f, ctx):
    w = POLICY["risk_weights"]
    s = w["base"]
    if f["dispute_signal"]:
        s += w["dispute_signal"]
    if f["sarcasm"]:
        s += w["sarcasm"]
    if f["hidden_aggression"] or f["explicit_aggression"]:
        s += w["aggression"]
    if f["intent"] in MONEY_INTENTS:
        s += w["money"]
    s += min(ctx["contacts_last_30d"], w["max_contacts"]) * w["per_recent_contact"]
    s = round(min(s, 1.0), 2)
    bands = POLICY["risk_bands"]
    band = "high" if s >= bands["high"] else "medium" if s >= bands["medium"] else "low"
    return s, band


def _floor_cents(x):
    return math.floor(x * 100 + 1e-6) / 100


def priority(f, band, disputed):
    if f["injection"] or f["intent"] == "account_security" or band == "high" or disputed:
        return "P1"
    if band == "medium" or f["intent"] == "account_access":
        return "P2"
    return "P4" if f["intent"] in ("question", "billing_question", "unclear") else "P3"


def decide(f, ctx, confidence=1.0):
    """Features + customer context -> actions, execution mode, refund amount."""
    flags = ("sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection")
    if (not isinstance(f, dict) or f.get("intent") not in INTENTS
            or any(type(f.get(k)) is not bool for k in flags)
            or type(confidence) not in (int, float)
            or not math.isfinite(confidence) or not 0 <= confidence <= 1):
        return {"actions": [], "mode": "escalate", "refund_amount": 0.0,
                "risk_score": 0.0, "risk_band": "low", "priority": "P1",
                "reasons": ["invalid_features_or_confidence"]}
    score, band = risk_score(f, ctx)
    lp = ctx["last_payment"]
    disputed = lp["status"] == "disputed"
    intent = f["intent"]
    reasons = []

    def out(actions, mode, refund=0.0):
        if refund > 0:
            if lp["status"] != "succeeded" or lp["days_ago"] < 0:
                refund = 0.0
                actions = [a for a in actions if not a.startswith("refund_")]
                mode = "suggest" if actions else "escalate"
                reasons.append("payment_not_refundable")
            elif refund > POLICY["auto_refund_limit_usd"]:
                mode = "suggest"
                reasons.append("above_auto_refund_limit")
            if refund and (ctx["refunds_last_180d"] or ctx["chargebacks_lifetime"]):
                mode = "suggest"
                reasons.append("refund_or_chargeback_history")
        if mode == "auto" and confidence < POLICY["min_auto_confidence"]:
            mode = "suggest"
            reasons.append("low_confidence")
        if not actions and mode != "escalate":
            actions = ["reply_only"]
        return {"actions": sorted(actions), "mode": mode, "refund_amount": round(refund, 2),
                "risk_score": score, "risk_band": band,
                "priority": priority(f, band, disputed), "reasons": reasons}

    if f["injection"]:
        reasons.append("prompt_injection: no actions, human review")
        return out([], "escalate")
    if disputed:
        reasons.append("dispute_already_open: refunding now would pay twice")
        return out(["evidence_pack"], "escalate")
    if intent in ("account_access", "account_security"):
        reasons.append("identity_verification_required")
        return out([], "escalate")
    if intent == "unclear":
        return out(["ask_clarification"], "auto")

    processor = ctx.get("billing_processor") or {
        "web": "card", "ios": "app_store", "android": "google_play"
    }.get(ctx["platform"])
    if processor not in ("card", "app_store", "google_play"):
        reasons.append("unknown_billing_processor")
        return out([], "escalate")
    if processor != "card":
        if intent == "pause":
            reasons.append("store_pause_requires_store_workflow")
            return out([], "escalate")
        acts = []
        if intent in MONEY_INTENTS:
            acts.append("store_refund_instructions")
        if intent in CANCEL_INTENTS:
            acts.append("store_cancel_instructions")
        if acts:
            reasons.append("store_billing: only Apple/Google can refund or cancel")
            return out(acts, "auto")

    acts, refund, mode = [], 0.0, "auto"
    if intent in CANCEL_INTENTS and ctx["subscription_status"] in ("active", "paused"):
        acts.append("cancel")

    if intent == "duplicate_charge":
        if not ctx["duplicate_payment_id"]:
            reasons.append("duplicate_not_verified")
            return out(["ask_clarification"], "suggest")
        acts.append("refund_duplicate")
        refund = lp["amount"]
    elif intent in REFUND_INTENTS:
        days, sessions = lp["days_ago"], ctx["sessions_since_last_charge"]
        share = POLICY["partial_refund_share"]
        if days <= POLICY["full_refund_max_days"] and sessions <= POLICY["full_refund_max_sessions"]:
            acts.append("refund_full")
            refund = lp["amount"]
        elif days <= POLICY["partial_refund_max_days"]:
            acts.append("refund_partial")
            refund = _floor_cents(lp["amount"] * share)
        elif band == "high":
            acts.append("refund_partial_goodwill")
            refund = _floor_cents(lp["amount"] * share)
            mode = "suggest"
            reasons.append("outside_window_but_high_dispute_risk: goodwill needs a human")
        else:
            reasons.append("outside_refund_window")
    elif intent == "pause":
        acts.append("pause")
    elif intent == "technical_issue" and (ctx["ltv_usd"] >= POLICY["goodwill_ltv_usd"]
                                          or band in ("medium", "high")):
        acts.append("extend_access_7d")
        mode = "suggest"

    return out(acts, mode, refund)
