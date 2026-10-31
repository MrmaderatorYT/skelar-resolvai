"""Naive keyword + lexicon-sentiment baseline: what a classic support bot "sees".

    python3 baseline_keywords.py && python3 evaluate.py data/predictions_baseline.json

Used on the slide to show why keyword rules fail: sarcasm reads as positive, and
injections slip through to auto-refunds.
"""
import json
import re

from reference_policy import DATA, load

POSITIVE = ["great", "love", "thank", "fantastic", "amazing", "super", "nice", "wow", "genial",
            "encanta", "toll", "danke", "дякую", "клас", "приємно", "honestly", "worth"]
NEGATIVE = ["scam", "stole", "angry", "worst", "bad", "never", "oszuści", "upsetting", "crash"]
KEYWORDS = [  # first match wins
    ("duplicate_charge", ["twice", "double", "x2"]),
    ("pause", ["pause"]),
    ("account_access", ["log in", "login", "password"]),
    ("cancel_refund", ["refund and cancel", "cancel and refund", "kündigen und geld"]),
    ("refund", ["refund", "money back", "повернути", "zurück", "devuelvan", "zwrot", "give it back"]),
    ("cancel", ["cancel", "скасу", "kündig", "cancelar"]),
    ("technical_issue", ["crash", "won't open", "stops", "not working"]),
    ("question", ["?"]),
]


def predict(text):
    low = text.lower()
    intent = next((i for i, kws in KEYWORDS if any(k in low for k in kws)), "unclear")
    pos = sum(low.count(w) for w in POSITIVE)
    neg = sum(low.count(w) for w in NEGATIVE)
    letters = re.findall(r"[A-Za-zА-Яа-яІіЇїЄє]", text)
    caps = sum(c.isupper() for c in letters) / max(len(letters), 1)
    return {
        "intent": intent,
        "sentiment": "positive" if pos > neg else "negative" if neg > pos else "neutral",
        "sarcasm": False,
        "hidden_aggression": False,
        "explicit_aggression": caps > 0.3 or neg > 0,
        "dispute_signal": any(k in low for k in ["bank", "chargeback", "банк"]),
        "injection": False,
    }


def main():
    preds = [{"ticket_id": t["id"], **predict(t["text"])} for t in load("tickets_eval")]
    (DATA / "predictions_baseline.json").write_text(json.dumps(preds, ensure_ascii=False, indent=2))
    print(f"wrote {len(preds)} predictions to data/predictions_baseline.json")


if __name__ == "__main__":
    main()
