import { useEffect, useState } from "react";
import { api } from "../api";
import CustomerPanel from "../components/CustomerPanel";
import DecisionCard from "../components/DecisionCard";
import HighlightedText from "../components/HighlightedText";
import PriorityPill from "../components/PriorityPill";
import ReplyEditor from "../components/ReplyEditor";
import SignalChips from "../components/SignalChips";
import Timeline from "../components/Timeline";
import type { Signal, TicketDetail } from "../types";

const FLAGS: Signal[] = ["sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection"];

export default function TicketView({ id, back }: { id: string; back: () => void }) {
  const [d, setD] = useState<TicketDetail | null>(null);
  const [reply, setReply] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = async () => {
    const detail = await api.ticket(id);
    setD(detail);
    setReply(detail.analysis?.reply ?? "");
  };
  useEffect(() => { load().catch((e) => setError(String(e))); }, [id]);

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("");
    try { await fn(); await load(); } catch (e) { setError(String(e)); } finally { setBusy(false); }
  };

  if (!d) return <p>Loading…</p>;
  const a = d.analysis;
  const f = a?.features;

  return (
    <div className="space-y-4">
      <button onClick={back} className="text-sm text-slate-500 hover:text-slate-900">← Queue</button>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <div className="grid gap-4 lg:grid-cols-[1.2fr_1fr_0.8fr]">
        <div className="space-y-4">
          <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
            <div className="flex items-center gap-2 text-xs text-slate-500">
              {a && <PriorityPill p={a.decision.priority} />}
              <span>{d.ticket.id} · {d.ticket.channel} · {d.ticket.language.toUpperCase()}</span>
            </div>
            <HighlightedText text={d.ticket.text} evidence={f?.evidence ?? []} />
            {f && (
              <>
                <SignalChips signals={FLAGS.filter((x) => f[x])} />
                <div className="rounded-lg bg-sky-50 p-3 text-sm">
                  <div><span className="text-slate-500">Intent:</span> <b>{f.intent.replaceAll("_", " ")}</b></div>
                  {f.true_goal && <div><span className="text-slate-500">Real goal:</span> {f.true_goal}</div>}
                  <div className="text-xs text-slate-500 mt-1">Literal tone: {f.sentiment}{f.sarcasm && f.sentiment === "positive" && " — but it's sarcasm"}</div>
                </div>
              </>
            )}
            {a?.status === "needs_approval" && <button disabled={busy} onClick={() => run(() => api.analyze(id, true))} className="w-full rounded-md border border-slate-300 py-2 text-sm disabled:opacity-50">Reanalyze (automatic actions may run)</button>}
            {!a && (
              <button disabled={busy} onClick={() => run(() => api.analyze(id))} className="w-full rounded-md bg-slate-900 text-white py-2 font-semibold disabled:opacity-50">
                {busy ? "Analyzing…" : "Analyze with Defuse"}
              </button>
            )}
          </section>
          {a && <ReplyEditor value={reply} onChange={setReply} editable={a.status === "needs_approval"} />}
        </div>
        <div className="space-y-4">
          {a && <DecisionCard a={a} onApprove={() => run(() => api.approve(id, reply))} onReject={() => run(() => api.reject(id, "agent escalated"))} />}
          <Timeline events={d.audit} />
        </div>
        <CustomerPanel c={d.customer} />
      </div>
    </div>
  );
}
