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
    "partial_refund_max_days": 30,  # partial refund = pro-rata unused days (CRD 14(3), CJEU C-641/19)
    "plan_period_days": {"monthly": 30, "quarterly": 91, "annual": 365},
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
    "retention": {
        # one save offer per ticket, only for the right customer; everyone else gets an honest cancel
        "offers": {"too_expensive": "offer_discount", "no_time": "offer_pause", "not_using": "offer_pause"},
        "discount_percent": 30,
        "discount_months": 3,
        "pause_months": 2,
        "min_tenure_months": 2,
        "min_paid_renewals": 1,
        "cooldown_days": 90,
        "max_offers_per_30d": 50,
        "holdout_bucket": 3,  # customers whose id % 10 == 3 never get offers: measures real uplift
    },
    "money": {
        "dispute_fee_usd": 15.0,                # Stripe US dispute fee
        "scheme_fee_per_dispute_usd": 0.0,      # set from the acquirer contract; not published by Visa
        "merchant_region": "EU",
        "vamp_threshold_by_region": {"US": 0.015, "CA": 0.015, "EU": 0.015, "AP": 0.015, "CEMEA": 0.022},
    },
}

CANCEL_REASONS = {"too_expensive", "no_time", "not_using", "cant_find_cancel", "technical", "other", "none"}

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
    subs = [s for s in data["subscriptions"] if s["customer_id"] == customer_id]
    if len(subs) == 1:
        sub, store_sub = subs[0], None
    else:
        cards = [s for s in subs if s["processor"] == "card"]
        stores = [s for s in subs if s["processor"] == "app_store" and s["status"] == "canceled"]
        links = cust.get("linked_subscriptions") or []
        if len(subs) != 2 or len(cards) != 1 or len(stores) != 1 or len(links) != 2 or set(links) != {cards[0]["id"], stores[0]["id"]}:
            raise ValueError("ambiguous_subscriptions: human review required")
        sub, store_sub = cards[0], stores[0]
    usage = next(u for u in data["usage"] if u["customer_id"] == customer_id)
    pays = sorted((p for p in data["payments"] if p["customer_id"] == customer_id),
                  key=lambda p: p["created_at"])
    if not pays:
        raise ValueError("no_payments: human review required")
    if store_sub is None:
        last = pays[-1]
    else:
        card_pays = [p for p in pays if p.get("subscription_id") == sub["id"] and p["processor"] == "card"]
        if not card_pays: raise ValueError("unverified_card_payment: human review required")
        last = card_pays[-1]
    same_day = [p for p in pays if (store_sub is None or (p.get("subscription_id") == sub["id"] and p["processor"] == "card"))
                and p["status"] == "succeeded" and p["amount"] == last["amount"]
                and p["created_at"][:10] == last["created_at"][:10]]
    ret = POLICY["retention"]

    def recent_offers(c):
        last_offer = c.get("last_offer_at")
        return c.get("offers_12m", 0) if last_offer and _days_ago(last_offer) <= ret["cooldown_days"] else 0

    fingerprint = cust.get("card_fingerprint")
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
        "period_days": POLICY["plan_period_days"].get(sub["plan"]),
        "price": sub["price"],
        "last_payment": {"id": last["id"], "amount": last["amount"], "status": last["status"],
                         "days_ago": _days_ago(last["created_at"])},
        "duplicate_payment_id": same_day[-1]["id"] if len(same_day) > 1 else None,
        "sessions_since_last_charge": usage["sessions_since_last_charge"],
        "cancel_flow_abandoned": usage["cancel_flow_abandoned"],
        # retention profile
        "acquisition_channel": cust.get("acquisition_channel"),
        "tenure_months": _days_ago(cust["signup_at"]) // 30,
        "paid_renewals": sum(1 for p in pays if p["type"] == "renewal" and p["status"] == "succeeded"),
        "offers_12m": recent_offers(cust),
        "identity_offers_12m": sum(recent_offers(c) for c in data["customers"]
                                   if fingerprint and c["id"] != customer_id
                                   and c.get("card_fingerprint") == fingerprint),
        "retention_offers_30d": sum(1 for c in data["customers"] if c.get("last_offer_at")
                                    and _days_ago(c["last_offer_at"]) <= 30),
        "in_holdout": int(customer_id.split("_")[-1]) % 10 == ret["holdout_bucket"],
        "cross_rail": None if store_sub is None else {"store_subscription_id": store_sub["id"], "store_status": store_sub["status"],
            "card_subscription_id": sub["id"], "card_status": sub["status"], "card_payment_id": last["id"]},
    }


def retention_offer(f, ctx):
    """(offer_action, None) if a save offer is allowed, (None, why_not) otherwise, (None, None) if
    the cancel reason is not one we make offers for."""
    ret = POLICY["retention"]
    offer = ret["offers"].get(f.get("cancel_reason"))
    if not offer:
        return None, None
    checks = [
        (f["dispute_signal"] or f["hidden_aggression"] or f["explicit_aggression"], "dispute_or_aggression"),
        (ctx.get("contacts_last_30d", 0) > 0, "repeat_contact"),
        (ctx.get("tenure_months", 0) < ret["min_tenure_months"]
         or ctx.get("paid_renewals", 0) < ret["min_paid_renewals"], "tenure"),
        (ctx.get("offers_12m", 0) > 0, "offer_cooldown"),
        (ctx.get("identity_offers_12m", 0) > 0, "identity_cluster_offer"),
        (ctx["refunds_last_180d"] > 0 or ctx["chargebacks_lifetime"] > 0, "refund_or_chargeback_history"),
        (ctx.get("retention_offers_30d", 0) >= ret["max_offers_per_30d"], "monthly_offer_budget"),
        (ctx.get("in_holdout", False), "retention_holdout"),
    ]
    why = next((reason for failed, reason in checks if failed), None)
    return (None, why) if why else (offer, None)


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


def _pro_rata(amount, days_ago, period_days):
    """Refund for the unused part of the paid period, floored to cents."""
    if not period_days or period_days <= 0:
        return 0.0
    unused = max(period_days - max(days_ago, 0), 0)
    return _floor_cents(amount * unused / period_days)


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

    # a cross-rail account is reconciled, not saved: no discount on a plan the customer already left in the store
    if intent == "cancel" and not ctx.get("cross_rail") and ctx["subscription_status"] in ("active", "paused"):
        offer, why_not = retention_offer(f, ctx)
        if offer:
            reasons.append("retention_offer: one offer; the customer can still cancel at any time")
            return out([offer], "auto")
        if why_not:
            reasons.append(f"retention_not_eligible: {why_not}")

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
        if days <= POLICY["full_refund_max_days"] and sessions <= POLICY["full_refund_max_sessions"]:
            acts.append("refund_full")
            refund = lp["amount"]
        elif days <= POLICY["partial_refund_max_days"]:
            acts.append("refund_partial")
            refund = _pro_rata(lp["amount"], days, ctx.get("period_days"))
        elif band == "high":
            acts.append("refund_partial_goodwill")
            refund = _pro_rata(lp["amount"], days, ctx.get("period_days"))
            mode = "suggest"
            reasons.append("outside_window_but_high_dispute_risk: goodwill needs a human")
        else:
            reasons.append("outside_refund_window")
        if any(a in acts for a in ("refund_partial", "refund_partial_goodwill")):
            if not ctx.get("period_days"):
                acts = [a for a in acts if not a.startswith("refund_")]
                refund, mode = 0.0, "suggest"
                reasons.append("unknown_plan_period: human decides the amount")
            elif refund <= 0:
                acts = [a for a in acts if not a.startswith("refund_")]
                refund = 0.0
                reasons.append("nothing_left_to_refund: paid period already used")
    elif intent == "pause":
        acts.append("pause")
    elif intent == "technical_issue" and (ctx["ltv_usd"] >= POLICY["goodwill_ltv_usd"]
                                          or band in ("medium", "high")):
        acts.append("extend_access_7d")
        mode = "suggest"

    if ctx.get("cross_rail"):
        web_active = ctx["subscription_status"] in ("active", "paused")
        if intent in MONEY_INTENTS:
            # money on a cross-rail account is never automatic: stop the live web plan, hold the refund for a human
            if web_active and "cancel" not in acts:
                acts.append("cancel")
            mode = "suggest"
            reasons.append("cross_rail_reconciliation: verify store and web subscriptions before refund")
        if web_active and (intent in MONEY_INTENTS or intent in CANCEL_INTENTS):
            acts.append("store_cancel_instructions")
    return out(acts, mode, refund)

# Heuristic autonomy priors. Keep in sync with defuse-policy.yml until Python reads YAML.
GATE = {
    "auto_error_max": .03, "assist_error_max": .15, "auto_impact_max_usd": 50,
    "assist_impact_max_usd": 500, "auto_expected_loss_max_usd": 1.5,
    "priors": {"question": .01, "billing_question": .02, "cancel": .01, "cancel_refund": .02,
               "refund": .02, "duplicate_charge": .02, "pause": .02, "technical_issue": .05,
               "account_access": .10, "account_security": .10, "unclear": .50},
}
_reference_decide = decide

def decide(f, ctx, confidence=1.0):
    base = _reference_decide(f, ctx, confidence)
    if not isinstance(f, dict) or f.get("intent") not in INTENTS or type(confidence) not in (float, int) or not math.isfinite(confidence) or not 0 <= confidence <= 1:
        return base
    intent = f["intent"]
    p_err = round(max(GATE["priors"].get(intent, 1.0), 1 - confidence), 12)
    impact = base["refund_amount"]
    period = ctx.get("period_days") or 0
    if "offer_discount" in base["actions"] or "apply_discount" in base["actions"]:
        if period <= 0: level = "HUMAN"
        else:
            impact += ctx["price"] * 30 / period * POLICY["retention"]["discount_percent"] / 100 * POLICY["retention"]["discount_months"]
            level = None
    else: level = None
    if "extend_access_7d" in base["actions"]:
        if period <= 0: level = "HUMAN"
        else: impact += ctx["price"] * 7 / period
    expected_loss = p_err * impact
    if f["injection"] or ctx["last_payment"]["status"] == "disputed" or intent in ("account_access", "account_security"):
        level = "HUMAN"
    elif level is None:
        if base["actions"] and all(a in ("reply_only", "ask_clarification") for a in base["actions"]): level = "AUTO"
        elif p_err <= GATE["auto_error_max"] and impact <= GATE["auto_impact_max_usd"] and expected_loss <= GATE["auto_expected_loss_max_usd"]: level = "AUTO"
        elif p_err <= GATE["assist_error_max"] and impact <= GATE["assist_impact_max_usd"]: level = "ASSIST"
        else: level = "HUMAN"
    rank = {"auto": 0, "suggest": 1, "escalate": 2}
    gate_rank = {"AUTO": 0, "ASSIST": 1, "HUMAN": 2}[level]
    if gate_rank > rank[base["mode"]]:
        base["mode"] = "suggest" if gate_rank == 1 else "escalate"
        base["reasons"].append("autonomy_gate_" + level.lower())
    return base
