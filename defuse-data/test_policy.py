"""pytest gate for the policy engine. Run after generate_seed.py."""
import reference_policy
from evaluate import gold_check
from reference_policy import build_context, decide, load, load_all

DATA = load_all()
TICKETS = {t["id"]: t for t in load("tickets_eval")}


def run(tid, **override):
    t = TICKETS[tid]
    return decide({**t["gold"], **override}, build_context(t["customer_id"], DATA))


def test_gold_labels_match_reference_policy():
    assert gold_check(list(TICKETS.values()), DATA)


def test_injection_never_executes_actions():
    for tid in ("T29", "T30", "T31"):
        d = run(tid)
        assert d["mode"] == "escalate" and d["actions"] == []


def test_open_dispute_is_never_refunded():
    d = run("T37")
    assert d["refund_amount"] == 0 and d["actions"] == ["evidence_pack"]


def test_cross_rail_refund_is_held_and_stops_web_plan():
    d = run("T45", intent="refund")
    assert d["mode"] == "suggest"
    assert d["actions"] == ["cancel", "refund_full", "store_cancel_instructions"]
    assert d["refund_amount"] == 49.99


def test_refund_above_limit_needs_human():
    assert run("T11")["mode"] == "suggest"


def test_low_confidence_downgrades_auto_to_suggest():
    t = TICKETS["T01"]
    d = decide(t["gold"], build_context(t["customer_id"], DATA), confidence=0.5)
    assert d["mode"] == "escalate"


def test_store_purchases_get_instructions_not_refunds():
    d = run("T15")
    assert d["refund_amount"] == 0 and "store_refund_instructions" in d["actions"]


def case(tid, last_payment=None, **ctx_override):
    t = TICKETS[tid]
    ctx = {**build_context(t["customer_id"], DATA), **ctx_override}
    if last_payment:
        ctx["last_payment"] = {**ctx["last_payment"], **last_payment}
    return t["gold"], ctx


def test_gate_uses_prior_and_confidence_thresholds_not_one_minus_confidence():
    g, ctx = case("T01")
    modes = {c: decide(g, ctx, c)["mode"] for c in (1.0, .97, .90, .85, .50)}
    assert modes == {1.0: "auto", .97: "auto", .90: "suggest", .85: "suggest", .50: "escalate"}


def test_low_confidence_is_added_only_from_auto_and_matches_java():
    g, ctx = case("T11")
    assert decide(g, ctx, .5)["reasons"] == ["above_auto_refund_limit", "autonomy_gate_human", "low_confidence"]
    assert "low_confidence" not in decide(g, ctx, .9)["reasons"]


def test_auto_refund_limit_boundary_is_50():
    g, ctx = case("T07", last_payment={"amount": 50.00})
    d = decide(g, ctx)
    assert d["mode"] == "auto" and d["refund_amount"] == 50.00
    g, ctx = case("T07", last_payment={"amount": 50.01})
    d = decide(g, ctx)
    assert d["mode"] == "suggest" and "above_auto_refund_limit" in d["reasons"]


def test_sandbox_off_holds_uncalibrated_refunds(monkeypatch):
    monkeypatch.setitem(reference_policy.GATE, "sandbox", False)
    g, ctx = case("T01")
    d = decide(g, ctx)
    assert d["mode"] == "suggest" and d["actions"] == ["cancel", "refund_full"]
    assert any(r.startswith("risk_not_calibrated:") for r in d["reasons"])
    monkeypatch.setitem(reference_policy.GATE, "risk_status", "VALIDATED")
    assert decide(g, ctx)["mode"] == "auto"


def test_auto_refund_disabled_holds_refunds(monkeypatch):
    monkeypatch.setitem(reference_policy.GATE, "auto_refund_enabled", False)
    d = decide(*case("T32"))
    assert d["mode"] == "suggest" and any(r.startswith("risk_not_calibrated:") for r in d["reasons"])


def test_unknown_processor_is_never_inferred_from_device():
    d = decide(*case("T01", platform="ios", billing_processor=None))
    assert d["mode"] == "escalate" and d["actions"] == [] and "unknown_billing_processor" in d["reasons"]
    # Apple Pay on a web card is not App Store: the card rail decides
    assert decide(*case("T01", platform="ios", billing_processor="card"))["actions"] == ["cancel", "refund_full"]


def test_already_refunded_payment_is_not_refunded_again():
    d = decide(*case("T07", last_payment={"status": "refunded"}))
    assert d["refund_amount"] == 0 and not any(a.startswith("refund_") for a in d["actions"])
    assert any(r.startswith("already_refunded:") for r in d["reasons"])


def test_retention_is_cancel_first():
    for tid, offer in (("T39", "offer_discount"), ("T42", "offer_pause")):
        d = run(tid)
        assert d["actions"] == ["cancel", offer] and d["mode"] == "auto"
