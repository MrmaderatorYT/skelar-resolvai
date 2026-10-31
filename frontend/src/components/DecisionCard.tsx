import { ACTION_LABEL, usd } from "../api";
import type { Analysis } from "../types";
import RiskBadge from "./RiskBadge";

const MODE = {
  auto: { label: "AUTO — done", cls: "bg-emerald-600 text-white" },
  suggest: { label: "Needs approval", cls: "bg-amber-400 text-amber-950" },
  escalate: { label: "Escalated to human", cls: "bg-red-600 text-white" },
};

export default function DecisionCard({ a, onApprove, onReject, onAccept, onDecline }: {
  a: Analysis; onApprove: () => void; onReject: () => void; onAccept: () => void; onDecline: () => void;
}) {
  const d = a.decision;
  const pending = a.status === "needs_approval";
  const m = a.money;
  const done = new Set(a.executed.filter((e) => !e.error).map((e) => e.action as string));
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
      <div className="flex items-center gap-2 flex-wrap">
        <RiskBadge band={d.risk_band} score={d.risk_score} large />
        <span className={`rounded-md px-2 py-1 text-sm font-semibold ${a.status === "offer_sent" ? "bg-sky-600 text-white" : MODE[d.mode].cls}`}>
          {a.status === "offer_sent" ? "Offer sent — waiting for customer"
            : a.status === "resolved" && d.mode === "suggest" ? "Approved — done" : MODE[d.mode].label}
        </span>
        <span className="text-xs text-slate-400 ml-auto">{a.latency_ms} ms · conf {a.features.confidence.toFixed(2)}</span>
      </div>
      <ul className="space-y-1">
        {d.actions.length === 0 && <li className="text-slate-500">No actions — a human takes over</li>}
        {d.actions.map((x) => (
          <li key={x} className="flex items-center gap-2">
            {done.has(x) ? <span className="text-emerald-600">✓</span> : <span title="waiting for approval">⏳</span>}
            {ACTION_LABEL[x] ?? x}
            {x.startsWith("refund") && <b>{usd(d.refund_amount)}</b>}
          </li>
        ))}
      </ul>
      {d.reasons.length > 0 && (
        <ul className="text-xs text-slate-600 bg-slate-50 rounded p-2 space-y-0.5">
          {d.reasons.map((r) => <li key={r}>• {r}</li>)}
        </ul>
      )}
      {m.amount_at_risk > 0 && (
        <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-sm">
          Chargeback exposure: <b>{usd(m.amount_at_risk)}</b> charge + <b>{usd(m.fees_at_risk)}</b> fees (est.) ·
          P(chargeback) {Math.round(m.p_dispute * 100)}% · expected fees avoided <b>{usd(m.expected_fees_avoided)}</b>
        </div>
      )}
      {a.status === "offer_sent" && (
        <div className="space-y-2">
          <p className="text-xs text-slate-500">One offer only. If the customer says no or repeats "cancel", we cancel right away.</p>
          <div className="flex gap-2">
            <button onClick={onAccept} className="flex-1 rounded-md bg-sky-600 text-white py-2 font-semibold hover:bg-sky-500">Customer accepts</button>
            <button onClick={onDecline} className="flex-1 rounded-md border border-slate-300 py-2 hover:bg-slate-100">Customer declines → cancel</button>
          </div>
        </div>
      )}
      {pending && (
        <div className="flex gap-2">
          <button onClick={onApprove} className="flex-1 rounded-md bg-slate-900 text-white py-2 font-semibold hover:bg-slate-700">Approve</button>
          <button onClick={onReject} className="rounded-md border border-slate-300 px-4 py-2 hover:bg-slate-100">Escalate</button>
        </div>
      )}
    </section>
  );
}
