"""Independent review checks. Run: python -m pytest -q -p no:cacheprovider test_codex_invariants.py

Data invariants and security regressions are separate. Known defects intentionally
FAIL; they are not hidden with xfail. No network or paid API calls.
"""
import copy
import json
from collections import Counter
from datetime import datetime
from decimal import Decimal, ROUND_FLOOR
from pathlib import Path

import pytest

import evaluate
import reference_policy as policy

DATA = policy.load_all()
EVAL = policy.load("tickets_eval")
HISTORY = policy.load("tickets_history")
TICKETS = {t["id"]: t for t in EVAL}
PERIOD = {"monthly": 30, "quarterly": 91, "annual": 365}
PROCESSOR = {"web": "card", "ios": "app_store", "android": "google_play"}
MONEY_CAUSES = {"charged_after_trial", "price_surprise_renewal", "duplicate_charge", "cant_find_cancel"}
FEATURE_KEYS = ["intent", "sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection"]


def dt(value):
    return datetime.fromisoformat(value)


def cents(value):
    return Decimal(str(value)).quantize(Decimal("0.01"))


def payments(cid):
    return sorted((p for p in DATA["payments"] if p["customer_id"] == cid), key=lambda p: dt(p["created_at"]))


def case(tid="T01", intent=None):
    t = TICKETS[tid]
    f = {k: t["gold"][k] for k in FEATURE_KEYS}
    if intent is not None:
        f["intent"] = intent
    return f, copy.deepcopy(policy.build_context(t["customer_id"], DATA))


def test_01_keys_and_references():
    for name in ("customers", "subscriptions", "payments"):
        ids = [r["id"] for r in DATA[name]]
        assert len(ids) == len(set(ids)), name
    ids = {r["id"] for r in DATA["customers"]}
    for name in ("subscriptions", "payments", "usage"):
        assert {r["customer_id"] for r in DATA[name]} <= ids, name
    for rows in (EVAL, HISTORY):
        assert {r["customer_id"] for r in rows} <= ids
        assert len({r["id"] for r in rows}) == len(rows)
    assert Counter(r["customer_id"] for r in DATA["usage"]) == Counter({cid: 1 for cid in ids})
    for customer in DATA["customers"]:
        subs = [s for s in DATA["subscriptions"] if s["customer_id"] == customer["id"]]
        if customer.get("linked_subscriptions"):
            assert len(subs) == len(customer["linked_subscriptions"]) == 2
            assert {s["id"] for s in subs} == set(customer["linked_subscriptions"])
        else:
            assert len(subs) == 1


def test_02_ltv_reconciles():
    bad = []
    for c in DATA["customers"]:
        total = sum((cents(p["amount"]) for p in payments(c["id"]) if p["status"] == "succeeded"), Decimal(0))
        if cents(c["ltv_usd"]) != total:
            bad.append((c["id"], str(total), c["ltv_usd"]))
    assert not bad, bad


def test_03_no_future_payments_or_tickets():
    assert not [(r["id"], r["created_at"]) for rows in (DATA["payments"], EVAL, HISTORY) for r in rows if dt(r["created_at"]) > policy.NOW]
    assert not [t["id"] for t in EVAL if dt(t["created_at"]) <= dt(payments(t["customer_id"])[-1]["created_at"])]


def test_04_payment_sequence():
    bad = []
    for s in DATA["subscriptions"]:
        ps = payments(s["customer_id"])
        linked = next(c for c in DATA["customers"] if c["id"] == s["customer_id"]).get("linked_subscriptions")
        if linked:
            rail = [p for p in ps if p.get("subscription_id") == s["id"]]
            assert rail and all(p["processor"] == s["processor"] for p in rail)
            continue
        assert ps[0]["type"] == "trial" and cents(ps[0]["amount"]) == Decimal("0.99")
        assert len([p for p in ps if p["type"] == "trial"]) == 1
        assert (dt(ps[1]["created_at"]) - dt(ps[0]["created_at"])).total_seconds() == 7 * 86400
        for a, b in zip(ps[1:], ps[2:]):
            gap = (dt(b["created_at"]) - dt(a["created_at"])).total_seconds()
            duplicate = dt(a["created_at"]).date() == dt(b["created_at"]).date() and cents(a["amount"]) == cents(b["amount"])
            if gap != PERIOD[s["plan"]] * 86400 and not duplicate:
                bad.append((s["id"], a["id"], b["id"], gap))
        assert all(cents(p["amount"]) == cents(s["price"]) for p in ps[1:])
    assert not bad, bad


def test_05_subscription_dates():
    assert not [s["id"] for s in DATA["subscriptions"] if s["status"] == "active" and dt(s["current_period_end"]) <= policy.NOW]
    for s in DATA["subscriptions"]:
        if s["status"] != "canceled": continue
        ps = payments(s["customer_id"])
        if any(p.get("subscription_id") == s["id"] for p in ps): ps = [p for p in ps if p.get("subscription_id") == s["id"]]
        assert s["canceled_at"] and dt(s["canceled_at"]) > dt(ps[-1]["created_at"]), s["id"]


def test_06_usage_dates():
    assert not [u["customer_id"] for u in DATA["usage"] if dt(u["last_active_at"]) > policy.NOW]
    assert not [u["customer_id"] for u in DATA["usage"] if u["sessions_since_last_charge"] == 0 and dt(u["last_active_at"]) >= dt(payments(u["customer_id"])[-1]["created_at"])]


def test_07_processor_alignment():
    cs = {c["id"]: c for c in DATA["customers"]}
    subs = {s["id"]: s for s in DATA["subscriptions"]}
    for name in ("subscriptions", "payments"):
        for row in DATA[name]:
            c = cs[row["customer_id"]]
            if c.get("linked_subscriptions"):
                sub_id = row["id"] if name == "subscriptions" else row.get("subscription_id")
                assert sub_id in c["linked_subscriptions"] and subs[sub_id]["processor"] == row["processor"]
            else: assert row["processor"] == PROCESSOR[c["platform"]]


def test_08_synthetic_identity():
    assert all(c["email"].endswith("@example.com") for c in DATA["customers"])
    for path in policy.DATA.glob("*.json"):
        value = path.read_text().lower()
        assert "liven" not in value and "leaply" not in value, path.name


def test_09_history_invariants():
    bad = []
    for t in HISTORY:
        assert t["synthetic"] is True
        assert t["resolution_hours"] >= t["first_response_hours"] >= 0
        assert t["refund_amount"] == 0 or t["root_cause"] in MONEY_CAUSES
        bands = policy.POLICY["risk_bands"]
        band = "high" if t["risk_score"] >= bands["high"] else "medium" if t["risk_score"] >= bands["medium"] else "low"
        assert t["risk_band"] == band
        score, calculated = policy.risk_score(t, {"contacts_last_30d": t["contacts_last_30d"]})
        if (score, calculated) != (t["risk_score"], t["risk_band"]):
            bad.append(t["id"])
    assert not bad, bad


def test_10_gold_all_decision_fields():
    keys = ["actions", "mode", "refund_amount", "risk_band", "priority"]
    assert evaluate.DECISION_KEYS == keys
    assert evaluate.gold_check(EVAL, DATA)
    for t in EVAL:
        d = policy.decide(t["gold"], policy.build_context(t["customer_id"], DATA))
        assert {k: d[k] for k in keys} == {k: t["gold"][k] for k in keys}, t["id"]


def test_11_gold_refund_not_above_payment():
    assert not [t["id"] for t in EVAL if cents(t["gold"]["refund_amount"]) > cents(payments(t["customer_id"])[-1]["amount"])]


def test_12_gold_auto_refund_guards():
    bad = []
    for t in EVAL:
        g, ctx = t["gold"], policy.build_context(t["customer_id"], DATA)
        if g["mode"] == "auto" and g["refund_amount"] > 0:
            if g["refund_amount"] > 60 or ctx["refunds_last_180d"] or ctx["chargebacks_lifetime"] or ctx["last_payment"]["status"] != "succeeded":
                bad.append(t["id"])
    assert not bad, bad


@pytest.mark.parametrize("amount,refunds,chargebacks", [(89.99, 0, 0), (29.99, 1, 0), (29.99, 0, 1)])
def test_policy_duplicate_obeys_all_money_guards(amount, refunds, chargebacks):
    f, ctx = case("T32")
    ctx["last_payment"]["amount"] = amount
    ctx["refunds_last_180d"], ctx["chargebacks_lifetime"] = refunds, chargebacks
    d = policy.decide(f, ctx)
    assert d["mode"] != "auto", d


@pytest.mark.parametrize("status", ["refunded", "charged_back", "failed"])
def test_policy_no_refund_for_non_succeeded_payment(status):
    f, ctx = case()
    ctx["last_payment"]["status"] = status
    d = policy.decide(f, ctx)
    assert d["refund_amount"] == 0 and d["mode"] != "auto", d


def test_policy_negative_payment_age_is_not_refundable():
    f, ctx = case()
    ctx["last_payment"]["days_ago"] = -1
    d = policy.decide(f, ctx)
    assert d["refund_amount"] == 0 and d["mode"] != "auto", d


def test_policy_missing_duplicate_needs_investigation():
    f, ctx = case("T32")
    ctx["duplicate_payment_id"] = None
    d = policy.decide(f, ctx)
    assert d["mode"] != "auto" and "reply_only" not in d["actions"], d


@pytest.mark.parametrize("platform", ["ios", "android"])
def test_policy_store_duplicate_has_no_money_action(platform):
    f, ctx = case("T32")
    ctx["platform"] = platform
    ctx["billing_processor"] = PROCESSOR[platform]
    d = policy.decide(f, ctx)
    assert d["refund_amount"] == 0 and d["actions"] == ["store_refund_instructions"]


@pytest.mark.parametrize("platform", ["ios", "android"])
def test_policy_store_pause_has_no_write_action(platform):
    f, ctx = case("T34")
    ctx["platform"] = platform
    ctx["billing_processor"] = PROCESSOR[platform]
    d = policy.decide(f, ctx)
    assert "pause" not in d["actions"], d


def test_policy_ios_device_with_card_billing_can_refund():
    f, ctx = case()
    ctx["platform"], ctx["billing_processor"] = "ios", "card"
    assert policy.decide(f, ctx)["refund_amount"] == 49.99


def test_policy_web_device_with_store_billing_cannot_refund():
    f, ctx = case()
    ctx["platform"], ctx["billing_processor"] = "web", "app_store"
    assert policy.decide(f, ctx)["refund_amount"] == 0


@pytest.mark.parametrize("amount", [29.99, 49.99, 89.99])
def test_floor_cents_matches_decimal(amount):
    expected = (Decimal(str(amount)) * Decimal("0.5")).quantize(Decimal("0.01"), rounding=ROUND_FLOOR)
    assert cents(policy._floor_cents(amount * 0.5)) == expected


def test_eval_gate_rejects_incomplete_predictions():
    f, _ = case("T18")
    assert evaluate.score(EVAL, [{"ticket_id": "T18", **f}], DATA) is False


def test_eval_gate_rejects_actions_under_escalated_injection():
    preds = [{"ticket_id": t["id"], **{k: t["gold"][k] for k in FEATURE_KEYS}} for t in EVAL]
    next(p for p in preds if p["ticket_id"] == "T29")["decision"] = {"mode": "escalate", "actions": ["refund_full"], "refund_amount": 29.99}
    assert evaluate.score(EVAL, preds, DATA) is False


def test_gold_check_detects_mutation_of_each_field(capsys):
    t = TICKETS["T01"]
    replacements = {"actions": [], "mode": "suggest", "refund_amount": 0, "risk_band": "low", "priority": "P4"}
    for key, value in replacements.items():
        changed = copy.deepcopy(t)
        changed["gold"][key] = value
        assert evaluate.gold_check([changed], DATA) is False, key


if __name__ == "__main__":
    # Inspectable distributions; no writes, independent of pytest availability.
    for key in ("language",):
        print("eval", key, dict(Counter(t[key] for t in EVAL)))
    for key in ("intent", "risk_band", "mode"):
        print("eval", key, dict(Counter(t["gold"][key] for t in EVAL)))
    print("auto_refunds", [t["id"] for t in EVAL if t["gold"]["mode"] == "auto" and t["gold"]["refund_amount"] > 0])
    print("history_root_causes", dict(Counter(t["root_cause"] for t in HISTORY)))
    for band in ("low", "medium", "high"):
        ts = [t for t in HISTORY if t["risk_band"] == band]
        count = sum(t["chargeback_filed_after"] for t in ts)
        print("history", band, count, "/", len(ts), "=", count / len(ts))
