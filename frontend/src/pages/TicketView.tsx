import { useEffect, useState } from "react";
import { api, ApiError, errorText, reasonPrefix } from "../api";
import CustomerPanel from "../components/CustomerPanel";
import DecisionCard from "../components/DecisionCard";
import HighlightedText from "../components/HighlightedText";
import PaymentRails from "../components/PaymentRails";
import PriorityPill from "../components/PriorityPill";
import ReplyEditor from "../components/ReplyEditor";
import SignalChips, { SIGNAL } from "../components/SignalChips";
import Timeline from "../components/Timeline";
import type { Signal, TicketDetail } from "../types";

const FLAGS: Signal[] = ["sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection"];
const UNVERIFIED = new Set(["identity_verification_required", "ambiguous_subscriptions"]);

export default function TicketView({ id, back }: { id: string; back: () => void }) {
  const [d, setD] = useState<TicketDetail | null>(null);
  const [reply, setReply] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);

  const load = async () => {
    const detail = await api.ticket(id);
    setD(detail);
    setReply(detail.analysis?.reply ?? "");
  };
  useEffect(() => { setD(null); setError(""); load().catch((e) => setError(errorText(e))); }, [id]);

  const run = async (fn: () => Promise<unknown>) => {
    if (busy) return;
    setBusy(true); setError(""); setConflict(false);
    try { await fn(); await load(); }
    catch (e) { setError(errorText(e)); if (e instanceof ApiError && e.status === 409) setConflict(true); }
    finally { setBusy(false); }
  };

  const backLink = <button onClick={back} className="text-sm text-slate-600 hover:text-slate-900">← Queue</button>;
  if (error && !d) return (
    <div className="space-y-3">{backLink}
      <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>
      <button onClick={() => { setError(""); load().catch((e) => setError(errorText(e))); }} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm">Retry</button>
    </div>
  );
  if (!d) return <p role="status">Loading…</p>;
  const a = d.analysis;
  const f = a?.features;
  // Gold/replay analyses may carry no recorded_at; the audit "analyzed" event is when the billing state was read.
  const verifiedAt = a?.recorded_at ?? [...d.audit].reverse().find((e) => e.event === "analyzed")?.at ?? null;
  const verified = a ? !a.decision.reasons.some((r) => UNVERIFIED.has(reasonPrefix(r))) : null;
  const sources = [...new Set([
    ...(d.customer.subscriptions ?? (d.customer.subscription ? [d.customer.subscription] : [])).map((s) => s.id).filter(Boolean),
    a?.context?.last_payment?.id, a?.context?.duplicate_payment_id,
    ...(a?.executed ?? []).flatMap((e) => [e.payment_id, e.subscription_id]),
  ].filter((x): x is string => typeof x === "string" && x.length > 0))];

  return (
    <div className="space-y-4">
      {backLink}
      {error && !a && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</p>}
      <div className="grid gap-4 lg:grid-cols-[1.2fr_1fr_0.9fr]">
        <div className="space-y-4 min-w-0">
          <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
            <div className="flex flex-wrap items-center gap-2 text-xs text-slate-600">
              {a && <PriorityPill p={a.decision.priority} />}
              <span>{d.ticket.id} · {d.ticket.channel} · {d.ticket.language.toUpperCase()}</span>
              {verified !== null && (
                <span className={`ml-auto rounded px-1.5 py-0.5 font-semibold ${verified ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-900"}`}>
                  {verified ? "✓ Verified demo session" : "⚠ Needs verification"}
                </span>
              )}
            </div>
            <HighlightedText text={d.ticket.text} evidence={f?.evidence ?? []} />
            {f && (
              <>
                <SignalChips signals={FLAGS.filter((x) => f[x])} />
                <div className="rounded-lg bg-sky-50 p-3 text-sm space-y-0.5">
                  <div><span className="text-slate-600">Intent:</span> <b>{f.intent.replaceAll("_", " ")}</b>
                    {f.intents && f.intents.length > 1 && <span className="text-slate-600"> · also {f.intents.filter((x) => x !== f.intent).map((x) => x.replaceAll("_", " ")).join(", ")}</span>}</div>
                  {f.true_goal && <div><span className="text-slate-600">Real goal:</span> {f.true_goal}</div>}
                  <div className="text-xs text-slate-600">Literal tone: {f.sentiment}{f.sarcasm && f.sentiment === "positive" && " — but it's sarcasm"}</div>
                  {(f.explicit_cancel || f.explicit_refund || f.bank_escalation) && (
                    <div className="text-xs text-slate-700">{[f.explicit_cancel && "explicit cancel", f.explicit_refund && "explicit refund", f.bank_escalation && "mentions bank / chargeback"].filter(Boolean).join(" · ")}</div>
                  )}
                  {f.missing_information && f.missing_information.length > 0 && <div className="text-xs text-amber-900">Missing: {f.missing_information.join(", ")}</div>}
                </div>
                {(f.evidence.length > 0 || sources.length > 0 || a?.policy_version) && (
                  <div className="space-y-1">
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600">Evidence &amp; sources</h3>
                    <ul className="flex flex-wrap gap-1 text-xs">
                      {f.evidence.map((e, i) => (
                        <li key={`e${i}`} className={`max-w-full truncate rounded px-1.5 py-0.5 ${SIGNAL[e.signal]?.chip ?? "bg-slate-100"}`} title={e.quote}>
                          {SIGNAL[e.signal]?.label ?? e.signal}: “{e.quote}”{typeof e.start === "number" && typeof e.end === "number" && <span className="opacity-80"> @{e.start}–{e.end}</span>}
                        </li>
                      ))}
                      {sources.map((s) => <li key={s} className="rounded border border-slate-300 px-1.5 py-0.5 font-mono text-slate-800">{s}</li>)}
                      {a?.policy_version && <li className="rounded border border-slate-300 px-1.5 py-0.5 text-slate-800">policy {a.policy_version}</li>}
                    </ul>
                  </div>
                )}
              </>
            )}
            {(a?.status === "needs_approval" || conflict) && <button disabled={busy} onClick={() => run(() => api.analyze(id, true))} className="w-full rounded-md border border-slate-300 py-2 text-sm disabled:opacity-50">Reanalyze (automatic actions may run)</button>}
            {!a && (
              <button disabled={busy} onClick={() => run(() => api.analyze(id))} className="w-full rounded-md bg-slate-900 text-white py-2 font-semibold disabled:opacity-50">
                {busy ? "Analyzing…" : "Analyze with Defuse"}
              </button>
            )}
          </section>
          {a && <ReplyEditor value={reply} onChange={setReply} editable={a.status === "needs_approval" || a.status === "escalated"} />}
        </div>
        <div className="space-y-4 min-w-0">
          {a && <DecisionCard a={a} busy={busy} error={error} replyReady={reply.trim().length > 0}
            onApprove={() => run(() => api.approve(id, a.analysis_id, reply, a.action_hash))}
            onReject={(reason) => run(() => api.reject(id, reason))}
            onTakeOver={() => run(() => api.humanReply(id, reply.trim()))}
            onAccept={() => run(() => api.acceptOffer(id))} onDecline={() => run(() => api.declineOffer(id))} />}
          <Timeline events={d.audit} />
        </div>
        <div className="space-y-4 min-w-0">
          <PaymentRails c={d.customer} a={a} verifiedAt={verifiedAt} />
          <CustomerPanel c={d.customer} />
        </div>
      </div>
    </div>
  );
}
