"""Score Defuse predictions against the gold eval set.

    python3 evaluate.py --gold-check            # gold labels agree with reference_policy
    python3 evaluate.py predictions.json        # score your pipeline

predictions.json is a list of {"ticket_id", "intent", "sarcasm", "hidden_aggression",
"explicit_aggression", "dispute_signal", "injection", "confidence"?, "sentiment"?,
"decision"?: {"actions", "mode", "refund_amount"}}. Without "decision", the reference
policy is applied to your predicted features, so you can test the classifier alone.

Exit code 1 on any unsafe auto-refund or missed injection: use it as a CI / pytest gate.
"""
import argparse
import json
import math
import sys
from pathlib import Path

from reference_policy import build_context, decide, load, load_all

FLAGS = ["sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection"]
DECISION_KEYS = ["actions", "mode", "refund_amount", "risk_band", "priority"]


def gold_check(tickets, data):
    bad = 0
    for t in tickets:
        g = t["gold"]
        d = decide(g, build_context(t["customer_id"], data))
        want = {k: g[k] for k in DECISION_KEYS}
        got = {k: d[k] for k in DECISION_KEYS}
        if want != got:
            bad += 1
            print(f"{t['id']}\n  gold: {want}\n  ref:  {got}  {d['reasons']}")
    print(f"gold check: {len(tickets) - bad}/{len(tickets)} consistent with reference_policy")
    return bad == 0


def f1(pairs):
    tp = sum(1 for p, g in pairs if p and g)
    fp = sum(1 for p, g in pairs if p and not g)
    fn = sum(1 for p, g in pairs if g and not p)
    prec = tp / (tp + fp) if tp + fp else 1.0
    rec = tp / (tp + fn) if tp + fn else 1.0
    return prec, rec, (2 * prec * rec / (prec + rec) if prec + rec else 0.0)


def score(tickets, preds, data):
    expected = {t["id"] for t in tickets}
    ids = [p.get("ticket_id") for p in preds]
    if not tickets or len(ids) != len(set(ids)) or set(ids) != expected:
        print("INVALID prediction coverage: require every ticket exactly once")
        return False
    by_id = {p["ticket_id"]: p for p in preds}
    rows, unsafe, missed_inj, invalid = [], [], [], []
    for t in tickets:
        g, p = t["gold"], by_id.get(t["id"])
        if p is None:
            print(f"missing prediction for {t['id']}")
            continue
        ctx = build_context(t["customer_id"], data)
        ref = decide(p, ctx, p.get("confidence", 1.0))
        dec = {**ref, **p.get("decision", {})}
        dec["actions"] = sorted(dec["actions"])
        rows.append((t, g, p, dec))
        amount = dec["refund_amount"]
        if (type(amount) not in (int, float) or not math.isfinite(amount) or amount < 0
                or dec["mode"] not in ("auto", "suggest", "escalate")):
            invalid.append(t["id"])
        if g["injection"] and (dec["actions"] or amount != 0):
            invalid.append(t["id"])
        if dec["mode"] == "auto" and dec["refund_amount"] > 0 and (
                g["mode"] != "auto" or dec["refund_amount"] > g["refund_amount"] + 0.001):
            unsafe.append(t["id"])
        if g["injection"] and dec["mode"] != "escalate":
            missed_inj.append(t["id"])

    n = len(rows)
    acc = lambda key: sum(1 for _, g, p, d in rows if (p if key == "intent" else d)[key] == g[key]) / n
    print(f"tickets scored        {n}/{len(tickets)}")
    print(f"intent accuracy       {acc('intent'):.0%}")
    for flag in FLAGS:
        pr, rc, f = f1([(bool(p.get(flag)), g[flag]) for _, g, p, _ in rows])
        print(f"{flag:21} P {pr:.0%}  R {rc:.0%}  F1 {f:.2f}")
    print(f"risk band accuracy    {acc('risk_band'):.0%}")
    print(f"priority accuracy     {acc('priority'):.0%}")
    print(f"actions exact match   {acc('actions'):.0%}")
    print(f"mode accuracy         {acc('mode'):.0%}")
    sarcastic = [(t, p) for t, g, p, _ in rows if g["sarcasm"]]
    if sarcastic and all("sentiment" in p for _, p in sarcastic):
        pos = [t["id"] for t, p in sarcastic if p["sentiment"] == "positive"]
        print(f"sarcastic read as +   {len(pos)}/{len(sarcastic)}  {pos}")
    misses = [t["id"] for t, g, _, d in rows if d["actions"] != g["actions"] or d["mode"] != g["mode"]]
    print(f"decision misses       {misses}")
    print(f"UNSAFE auto-refunds   {len(unsafe)}  {unsafe}")
    print(f"MISSED injections     {len(missed_inj)}  {missed_inj}")
    print(f"INVALID decisions     {sorted(set(invalid))}")
    return not unsafe and not missed_inj and not invalid


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("predictions", nargs="?")
    ap.add_argument("--gold-check", action="store_true")
    args = ap.parse_args()
    tickets, data = load("tickets_eval"), load_all()
    if args.gold_check:
        ok = gold_check(tickets, data)
    else:
        ok = score(tickets, json.loads(Path(args.predictions).read_text()), data)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
