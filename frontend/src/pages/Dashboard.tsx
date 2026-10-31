import { useEffect, useState } from "react";
import { api, usd } from "../api";
import KpiCard from "../components/KpiCard";
import RootCauseChart from "../components/RootCauseChart";
import type { RootCause, Summary } from "../types";

export default function Dashboard() {
  const [s, setS] = useState<Summary | null>(null);
  const [rc, setRc] = useState<RootCause[]>([]);
  useEffect(() => { api.summary().then(setS); api.rootCauses().then(setRc); }, []);
  if (!s) return <p>Loading…</p>;
  const b = s.baseline;
  const top = rc[0];
  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold">Support performance</h1>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Auto-resolved" value={`${s.auto_resolved_pct}%`} hint={`${s.analyzed} of ${s.tickets_total} analyzed`} />
        <KpiCard label="Needs approval / escalated" value={`${s.by_status.needs_approval ?? 0} / ${s.by_status.escalated ?? 0}`} />
        <KpiCard label="Time to resolution" value={s.ai_median_latency_ms != null ? `${(s.ai_median_latency_ms / 1000).toFixed(1)} s` : "—"}
          hint={`baseline: ${b.median_resolution_hours} h median`} />
        <KpiCard label="Chargeback exposure (high risk)" value={usd(s.gross_exposure_usd)} hint="charge + est. chargeback & VAMP fees" />
        <KpiCard label="Refund spend (policy-entitled)" value={usd(s.refunded_usd)} hint="a refund is a cost, not a saving" />
        <KpiCard label="Expected fees avoided" value={usd(s.expected_fees_avoided_usd)} hint={`≈ ${s.expected_chargebacks_avoided} chargebacks resolved before the bank`} />
        <KpiCard label="Sarcasm / high risk caught" value={`${s.sarcasm_detected} / ${s.high_risk_tickets}`} />
        <KpiCard label="Injections blocked" value={s.injections_blocked} />
        <KpiCard label="Retention offers sent" value={s.retention.offers_sent}
          hint={`accepted ${s.retention.offers_accepted} · declined ${s.retention.offers_declined} · save rate ${s.retention.save_rate ?? "—"}% · holdout ${s.retention.holdout_customers}`} />
        <KpiCard label="Discount cost" value={usd(s.retention.discount_cost_usd)} hint="accepted discounts × price × % × months" />
        <KpiCard label="Fees per dispute" value={usd(s.money_settings.dispute_fee_usd + s.money_settings.scheme_fee_per_dispute_usd)}
          hint={`Stripe dispute fee + contract fee · VAMP ${s.money_settings.merchant_region} ${(s.money_settings.vamp_threshold * 100).toFixed(1)}%`} />
        <KpiCard label="Offers withheld (anti-abuse)" value={Object.values(s.retention.not_eligible).reduce((x, y) => x + y, 0)}
          hint={Object.entries(s.retention.not_eligible).map(([k, v]) => `${k.replaceAll("_", " ")} ${v}`).join(" · ") || "—"} />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="bg-white rounded-xl border border-slate-200 p-4">
          <h2 className="font-semibold">Why customers write (root causes)</h2>
          <RootCauseChart rows={rc} />
          {top && (
            <p className="text-sm bg-amber-50 rounded p-2 mt-2">
              <b>Top product fix:</b> {top.recommended_fix} — removes {Math.round(top.share * 100)}% of tickets.
            </p>
          )}
        </section>
        <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
          <h2 className="font-semibold">Baseline: {b.label}</h2>
          <table className="w-full text-sm">
            <thead className="text-xs text-slate-500 text-left"><tr><th>Risk band</th><th>Chargeback rate after ticket</th></tr></thead>
            <tbody>
              {(["high", "medium", "low"] as const).map((k) => (
                <tr key={k} className="border-t"><td className="py-1">{k}</td><td>{Math.round(b.p_dispute_by_band[k] * 1000) / 10}%</td></tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm text-slate-600">{b.tickets} tickets · median first response {b.median_first_response_hours} h · {b.chargebacks_filed} chargebacks filed.</p>
          <table className="w-full text-xs">
            <tbody>
              {rc.map((r) => (
                <tr key={r.root_cause} className="border-t"><td className="py-1">{r.root_cause.replaceAll("_", " ")}</td><td>{r.count}</td><td>cb {Math.round(r.chargeback_rate * 1000) / 10}%</td></tr>
              ))}
            </tbody>
          </table>
          <p className="text-xs text-slate-400">Estimates. Baseline data is synthetic.</p>
        </section>
      </div>
    </div>
  );
}
