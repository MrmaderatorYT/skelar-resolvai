import { useEffect, useId, useState, type FormEvent } from "react";
import { actionLabel, DEFAULT_TERMS, loadTerms, reasonLabel, reasonPrefix, ROUTE, usd, utc, type OfferTerms } from "../api";
import type { Analysis, Executed } from "../types";
import RiskBadge from "./RiskBadge";

const MODE = {
  auto: { label: "AUTO — done", cls: "bg-emerald-700 text-white" },
  suggest: { label: "REVIEW — needs approval", cls: "bg-amber-400 text-amber-950" },
  escalate: { label: "INVESTIGATE — human handles it", cls: "bg-red-700 text-white" },
};
// Informational reasons: they explain a decision but are not a blocker for AUTO.
const NOT_BLOCKERS = new Set(["autonomy_gate_auto", "retention_offer"]);

export default function DecisionCard({ a, busy, error, replyReady, onApprove, onReject, onTakeOver, onAccept, onDecline }: {
  a: Analysis; busy: boolean; error: string; replyReady: boolean;
  onApprove: () => void; onReject: (reason: string) => void; onTakeOver: () => void; onAccept: () => void; onDecline: () => void;
}) {
  const [terms, setTerms] = useState<OfferTerms>(DEFAULT_TERMS);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const reasonId = useId();
  useEffect(() => { loadTerms().then(setTerms); }, []);
  useEffect(() => { setRejecting(false); setReason(""); }, [a.analysis_id]);

  const d = a.decision;
  const pending = a.status === "needs_approval";
  const open = pending || a.status === "escalated";
  const byAction = new Map<string, Executed>();
  for (const e of a.executed ?? []) if (typeof e.action === "string") byAction.set(e.action, e);

  const refund = d.actions.find((x) => x.startsWith("refund"));
  const executedPayment = refund ? byAction.get(refund)?.payment_id : undefined;
  const paymentId = (typeof executedPayment === "string" ? executedPayment : undefined)
    ?? (refund === "refund_duplicate" ? a.context?.duplicate_payment_id : a.context?.last_payment?.id) ?? "unknown";
  const moneyLine = refund && d.refund_amount > 0 ? `REFUND ${usd(d.refund_amount)} USD → payment ${paymentId}` : "No money moves";

  const blockers = d.reasons.filter((r) => !NOT_BLOCKERS.has(reasonPrefix(r)));
  const why = d.mode === "auto" ? "Policy notes" : d.mode === "escalate" ? "Why a human must investigate" : "Why a human must approve";
  const statusLabel = a.status === "offer_sent" ? "AUTO — canceled · win-back offer open"
    : a.status === "resolved" && d.mode === "suggest" ? "REVIEW — approved, done" : MODE[d.mode]?.label ?? ROUTE[d.mode] ?? d.mode;
  const offer = d.actions.includes("offer_discount") ? `${terms.discountPercent}% off for ${terms.discountMonths} months`
    : d.actions.includes("offer_pause") ? `pause ${terms.pauseMonths} months instead of cancel` : null;

  function submitReject(e: FormEvent) { e.preventDefault(); if (reason.trim()) onReject(reason.trim()); }

  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3" aria-label="Decision">
      <div className="flex items-center gap-2 flex-wrap">
        <RiskBadge band={d.risk_band} score={d.risk_score} large />
        <span className={`rounded-md px-2 py-1 text-sm font-semibold ${a.status === "offer_sent" ? "bg-sky-700 text-white" : MODE[d.mode]?.cls ?? "bg-slate-200"}`}>{statusLabel}</span>
        <span className="text-xs text-slate-600 ml-auto">{a.latency_ms} ms · conf {a.features.confidence.toFixed(2)}</span>
      </div>

      <div className={`rounded-lg border p-3 font-mono text-sm font-semibold ${refund && d.refund_amount > 0 ? "border-amber-300 bg-amber-50 text-amber-950" : "border-slate-200 bg-slate-50 text-slate-800"}`}>
        {moneyLine}
      </div>

      <ul className="space-y-1 text-sm">
        {d.actions.length === 0 && <li className="text-slate-600">No actions — a human takes over</li>}
        {d.actions.map((x) => {
          const e = byAction.get(x);
          const state = !e ? (pending ? "waiting" : "not run") : e.pending ? "pending" : e.error ? "failed" : "done";
          const icon = { done: "✓", failed: "✗", pending: "⟳", waiting: "⏳", "not run": "·" }[state];
          return (
            <li key={x} className={`flex flex-wrap items-center gap-2 ${state === "failed" ? "text-red-700" : ""}`}>
              <span aria-hidden className={state === "done" ? "text-emerald-700" : ""}>{icon}</span>
              <span>{actionLabel(x, terms)}</span>
              {x.startsWith("refund") && <b>{usd(d.refund_amount)}</b>}
              <span className="text-xs text-slate-600">({state === "pending" ? "provider timeout — reconciling, not retried" : state})</span>
              {e?.error && !e.pending && <span className="w-full text-xs">{String(e.error)}</span>}
            </li>
          );
        })}
      </ul>

      {blockers.length > 0 && (
        <div className="rounded-lg bg-slate-50 p-3">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-700">{why}</h4>
          <ul className="mt-1 space-y-1 text-sm text-slate-800">
            {blockers.map((r) => <li key={r} title={r}>• {reasonLabel(r)}{r.includes(":") && <span className="block pl-3 text-xs text-slate-600">{r.slice(r.indexOf(":") + 1).trim()}</span>}</li>)}
          </ul>
        </div>
      )}

      {a.autonomy && (
        <div className="rounded-lg border border-slate-200 p-3 text-xs text-slate-700 space-y-1">
          <div className="font-semibold">Risk status: {a.autonomy.risk_status ?? "UNVALIDATED"} · heuristic prior p_err {(a.autonomy.p_err * 100).toFixed(1)}%</div>
          <div>Gate: {a.autonomy.level} · impact {usd(a.autonomy.impact_usd)} · heuristic expected loss {usd(a.autonomy.expected_loss_usd)}</div>
          <div className="text-slate-600">
            Error upper bound: {a.autonomy.p_bad_upper_95 == null ? "not measured" : `${(a.autonomy.p_bad_upper_95 * 100).toFixed(1)}%`} (n={a.autonomy.n_relevant ?? 0}).
            The prior is hand-set per intent, not a calibrated probability.
          </div>
        </div>
      )}

      {(a.action_hash || a.review_expires_at || a.policy_version) && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs text-slate-600">
          {a.action_hash && <><dt>Action hash</dt><dd className="font-mono text-slate-900" title={a.action_hash}>{a.action_hash.slice(0, 12)}…</dd></>}
          {a.review_expires_at && <><dt>Review expires</dt><dd className="text-slate-900">{utc(a.review_expires_at)}</dd></>}
          {a.policy_version && <><dt>Policy</dt><dd className="text-slate-900">{a.policy_version}</dd></>}
        </dl>
      )}

      {a.money && a.money.amount_at_risk > 0 && (
        <details className="rounded-lg border border-slate-200 p-3 text-xs text-slate-700">
          <summary className="cursor-pointer font-semibold">Dispute-fee scenario (illustrative, not measured)</summary>
          <p className="mt-2">If this became a chargeback: {usd(a.money.amount_at_risk)} charge + {usd(a.money.fees_at_risk)} fees (est.).
            Heuristic P(dispute) {Math.round(a.money.p_dispute * 100)}% · scenario fees {usd(a.money.dispute_fee_scenario)}. Not a measured outcome.</p>
        </details>
      )}

      {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}

      {a.status === "offer_sent" && (
        <div className="space-y-2">
          <p className="text-xs text-slate-700">The subscription is already canceled. One optional win-back offer is open{offer && <>: <b>{offer}</b></>}. Declining changes nothing further and closes the ticket.</p>
          <div className="flex flex-wrap gap-2">
            <button disabled={busy} onClick={onAccept} className="flex-1 rounded-md bg-sky-700 text-white py-2 font-semibold hover:bg-sky-600 disabled:opacity-50">Customer accepts offer</button>
            <button disabled={busy} onClick={onDecline} className="flex-1 rounded-md border border-slate-300 py-2 hover:bg-slate-100 disabled:opacity-50">Customer declines (stays canceled)</button>
          </div>
        </div>
      )}

      {open && (
        <div className="space-y-2">
          <div className="flex flex-wrap gap-2">
            {pending && <button disabled={busy} onClick={onApprove} className="flex-1 rounded-md bg-slate-900 text-white py-2 font-semibold hover:bg-slate-700 disabled:opacity-50">Approve</button>}
            {pending && <button disabled={busy} onClick={() => setRejecting((x) => !x)} aria-expanded={rejecting} className="rounded-md border border-slate-300 px-4 py-2 hover:bg-slate-100 disabled:opacity-50">Reject</button>}
            <button disabled={busy || !replyReady} onClick={onTakeOver} title="Send the reply draft as your own message; no automatic actions run"
              className="rounded-md border border-slate-300 px-4 py-2 hover:bg-slate-100 disabled:opacity-50">Take over</button>
          </div>
          {rejecting && (
            <form onSubmit={submitReject} className="space-y-2">
              <label htmlFor={reasonId} className="block text-xs font-semibold text-slate-700">Reason for rejecting (required)</label>
              <input id={reasonId} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} autoFocus required
                className="w-full rounded-md border border-slate-300 p-2 text-sm" placeholder="e.g. wrong payment, customer meant the App Store plan" />
              <button disabled={busy || !reason.trim()} className="rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">Reject decision</button>
            </form>
          )}
          <p className="text-xs text-slate-600">Approve re-checks billing state and policy on the server before anything runs. Take over sends the draft reply as yours; no money moves.</p>
        </div>
      )}
    </section>
  );
}
