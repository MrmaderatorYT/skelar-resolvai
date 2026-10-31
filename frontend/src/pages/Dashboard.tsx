import { useEffect, useState } from "react";
import { api, errorText, REVIEW_CODE_LABEL, usd } from "../api";
import KpiCard from "../components/KpiCard";
import RootCauseChart from "../components/RootCauseChart";
import SyntheticBanner from "../components/SyntheticBanner";
import type { Diagnostics, RootCause, Summary } from "../types";

export default function Dashboard() {
  const [s, setS] = useState<Summary | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [rc, setRc] = useState<RootCause[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    const fail = (e: unknown) => setError(errorText(e));
    api.summary().then(setS).catch(fail);
    api.rootCauses().then(setRc).catch(fail);
    api.diagnostics().then(setDiagnostics).catch(fail);
  }, []);
  if (error && !s) return <div className="space-y-3"><SyntheticBanner /><p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">Could not load the dashboard: {error}</p></div>;
  if (!s) return <p role="status">Loading…</p>;
  const b = s.baseline;
  const top = rc[0];
  const routes = diagnostics?.routes;
  const reviewReasons = Object.entries(diagnostics?.review_reasons ?? {}).filter(([, n]) => n > 0).sort((x, y) => y[1] - x[1]);
  return (
    <div className="space-y-6">
      <SyntheticBanner />
      <div>
        <h1 className="text-xl font-bold">Support performance</h1>
        <p className="text-xs text-slate-600">Demo session and synthetic history. Money figures are scenarios, not measured outcomes. Risk status: {diagnostics?.risk_status ?? "UNVALIDATED"}.</p>
      </div>
      {error && <p role="alert" className="text-sm text-red-700">Some panels failed to load: {error}</p>}
      <div className="grid gap-3 sm:grid-cols-3">
        <KpiCard label="AUTO" value={routes?.auto ?? "—"} hint="Policy allowed it to run without a person" />
        <KpiCard label="REVIEW" value={routes?.review ?? "—"} hint="Prepared; a person must approve" />
        <KpiCard label="INVESTIGATE" value={routes?.investigate ?? "—"} hint="Handed to a person; nothing runs" />
      </div>
      {diagnostics && <div className="grid gap-3 md:grid-cols-2">
        <section className="rounded-xl border border-slate-200 bg-white p-4">
          <h2 className="font-semibold">Why review was needed</h2>
          {reviewReasons.length ? <ul className="mt-2 space-y-1 text-sm">{reviewReasons.map(([code, n]) =>
            <li key={code} className="flex justify-between gap-2"><span>{REVIEW_CODE_LABEL[code] ?? code.replaceAll("_", " ").toLowerCase()}</span><b>{n}</b></li>)}</ul>
            : <p className="mt-2 text-xs text-slate-600">No review reasons recorded yet.</p>}
          <p className="mt-4 text-sm">Money actions blocked: <b>{diagnostics.blocked_money_actions ?? "—"}</b></p>
          <p className="mt-1 text-xs text-slate-600">Resolved in this session — AI auto: {diagnostics.resolved.auto} · AI + human: {diagnostics.resolved.assisted} · human reply: {diagnostics.resolved.human}</p>
        </section>
        <section className="rounded-xl border border-slate-200 bg-white p-4">
          <h2 className="font-semibold">Segments · synthetic history</h2>
          <p className="mt-1 text-xs text-slate-600">Acquisition channel · root cause · payment processor</p>
          {diagnostics.synthetic_segments.slice(0, 8).map((x) => <p key={x.segment} className="mt-2 text-xs text-slate-700">{x.segment.replaceAll("_", " ")} — {x.count}</p>)}
        </section>
      </div>}
      <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Auto-resolved" value={`${s.auto_resolved_pct}%`} hint={`${s.analyzed} of ${s.tickets_total} analyzed`} />
        <KpiCard label="Needs approval / escalated" value={`${s.by_status.needs_approval ?? 0} / ${s.by_status.escalated ?? 0}`} />
        <KpiCard label="AI analysis latency" value={s.ai_median_latency_ms != null ? `${(s.ai_median_latency_ms / 1000).toFixed(1)} s` : "—"} hint="median per analysis in this session" />
        <KpiCard label="Chargeback exposure (high risk)" value={usd(s.gross_exposure_usd)} hint="charge + est. chargeback & VAMP fees" />
        <KpiCard label="Refund spend (policy-entitled)" value={usd(s.refunded_usd)} hint="a refund is a cost" />
        <KpiCard label="Sarcasm / high risk flagged" value={`${s.sarcasm_detected} / ${s.high_risk_tickets}`} />
        <KpiCard label="Injections blocked" value={s.injections_blocked} />
        <KpiCard label="Win-back offers sent" value={s.retention.offers_sent}
          hint={`accepted ${s.retention.offers_accepted} · declined ${s.retention.offers_declined} · acceptance ${s.retention.acceptance_rate ?? "—"}% · holdout ${s.retention.holdout_customers}`} />
        <KpiCard label="Discount cost" value={usd(s.retention.discount_cost_usd)} hint="accepted discounts × price × % × months" />
        <KpiCard label="Fees per dispute" value={usd(s.money_settings.dispute_fee_usd + s.money_settings.scheme_fee_per_dispute_usd)}
          hint={`Stripe dispute fee + contract fee · VAMP ${s.money_settings.merchant_region} ${(s.money_settings.vamp_threshold * 100).toFixed(1)}%`} />
        <KpiCard label="Offers withheld (anti-abuse)" value={Object.values(s.retention.not_eligible).reduce((x, y) => x + y, 0)}
          hint={Object.entries(s.retention.not_eligible).map(([k, v]) => `${k.replaceAll("_", " ")} ${v}`).join(" · ") || "—"} />
      </div>
      <details className="rounded-xl border border-slate-200 bg-white p-4 text-sm">
        <summary className="cursor-pointer font-semibold">Dispute-fee scenario (illustrative, not measured)</summary>
        <p className="mt-2 text-slate-700">Heuristic dispute probability × fees, summed over resolved tickets: <b>{usd(s.dispute_fee_scenario_usd)}</b> across
          {" "}{s.dispute_scenario_count ?? "—"} scenario disputes. A thought experiment on synthetic data, not a measured or counterfactual result.</p>
      </details>
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="bg-white rounded-xl border border-slate-200 p-4 min-w-0">
          <h2 className="font-semibold">Why customers write (root causes)</h2>
          <RootCauseChart rows={rc} />
          {top && (
            <p className="text-sm bg-amber-50 rounded p-2 mt-2">
              <b>Top product fix:</b> {top.recommended_fix} — {Math.round(top.share * 100)}% of synthetic history tickets have this cause.
            </p>
          )}
        </section>
        <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3 min-w-0">
          <h2 className="font-semibold">Baseline: {b.label}</h2>
          <table className="w-full text-sm">
            <thead className="text-xs text-slate-600 text-left"><tr><th scope="col">Risk band</th><th scope="col">Chargeback rate after ticket</th></tr></thead>
            <tbody>
              {(["high", "medium", "low"] as const).map((k) => (
                <tr key={k} className="border-t"><td className="py-1">{k}</td><td>{Math.round((b.p_dispute_by_band[k] ?? 0) * 1000) / 10}%</td></tr>
              ))}
            </tbody>
          </table>
          <p className="text-sm text-slate-700">{b.tickets} synthetic tickets · {b.chargebacks_filed} chargebacks filed.</p>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <tbody>
                {rc.map((r) => (
                  <tr key={r.root_cause} className="border-t"><td className="py-1">{r.root_cause.replaceAll("_", " ")}</td><td>{r.count}</td><td>cb {Math.round(r.chargeback_rate * 1000) / 10}%</td></tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-xs text-slate-600">Estimates. Baseline data is synthetic.</p>
        </section>
      </div>
    </div>
  );
}
