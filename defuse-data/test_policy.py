"""pytest gate for the policy engine. Run after generate_seed.py."""
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
