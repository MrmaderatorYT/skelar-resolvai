import { useEffect, useId, useRef, useState } from "react";
import { api, errorText, ROUTE } from "../api";
import PriorityPill from "../components/PriorityPill";
import RiskBadge from "../components/RiskBadge";
import SignalChips from "../components/SignalChips";
import type { Customer, TicketRow } from "../types";

const STATUS: Record<string, string> = {
  open: "bg-slate-100 text-slate-700", resolved: "bg-emerald-100 text-emerald-800",
  needs_approval: "bg-amber-100 text-amber-900", escalated: "bg-red-100 text-red-800",
  offer_sent: "bg-sky-100 text-sky-800",
};

export default function Queue({ open, inbox = false }: { open: (id: string) => void; inbox?: boolean }) {
  const [rows, setRows] = useState<TicketRow[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showNew, setShowNew] = useState(false);
  const load = () => api.tickets().then(setRows).catch((e) => { setRows((r) => r ?? []); setError(errorText(e)); });
  useEffect(() => { load(); }, []);

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("");
    try { await fn(); await load(); } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  };
  const reset = () => { if (window.confirm("Reset the demo? All analyses, approvals and new tickets will be discarded.")) run(api.reset); };
  const shown = (rows ?? []).filter((r) => !inbox || r.status === "needs_approval" || r.status === "escalated");

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-xl font-bold">{inbox ? "Inbox · Needs human review" : "Support queue"}</h1>
        <span className="text-sm text-slate-600">{(rows ?? []).filter((r) => r.status === "open").length} open</span>
        <div className="ml-auto flex flex-wrap gap-2">
          <button disabled={busy} onClick={() => setShowNew(true)} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-white disabled:opacity-50">New ticket</button>
          <button disabled={busy} onClick={reset} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-white disabled:opacity-50">Reset demo</button>
          <button disabled={busy} onClick={() => run(api.analyzeAll)} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm font-semibold disabled:opacity-50">
            {busy ? "Working…" : "Analyze all"}
          </button>
        </div>
      </div>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="bg-white rounded-xl border border-slate-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-slate-600 border-b">
            <tr>{["Prio", "Risk", "Route", "Signals", "Customer", "Message", "Lang", "Status"].map((h) => <th key={h} scope="col" className="p-2">{h}</th>)}</tr>
          </thead>
          <tbody>
            {rows === null && <tr><td colSpan={8} className="p-4 text-slate-600">Loading…</td></tr>}
            {rows !== null && shown.length === 0 && (
              <tr><td colSpan={8} className="p-6 text-center text-slate-600">
                {inbox ? "Nothing needs human review right now." : "No tickets. Create one with New ticket or reset the demo."}
              </td></tr>
            )}
            {shown.map((r) => (
              <tr key={r.id} tabIndex={0} role="link" aria-label={`Open ticket ${r.id} from ${r.customer_name}`}
                onClick={() => open(r.id)} onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(r.id); } }}
                className={`border-b last:border-0 cursor-pointer hover:bg-slate-50 focus:outline-2 focus:outline-slate-900 ${r.priority === "P1" ? "bg-red-50/40" : ""}`}>
                <td className="p-2"><PriorityPill p={r.priority} /></td>
                <td><RiskBadge band={r.risk_band} /></td>
                <td className="text-xs font-semibold pr-2">{r.mode ? ROUTE[r.mode] ?? r.mode : "—"}</td>
                <td className="max-w-48"><SignalChips signals={r.signals} /></td>
                <td className="whitespace-nowrap pr-2">{r.customer_name}</td>
                <td className="text-slate-700 pr-2 min-w-48">{r.text_preview}{r.text.length > 90 && "…"}</td>
                <td className="uppercase text-xs">{r.language}</td>
                <td><span className={`rounded px-1.5 py-0.5 text-xs whitespace-nowrap ${STATUS[r.status] ?? ""}`}>{r.status.replaceAll("_", " ")}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {showNew && <NewTicket close={() => setShowNew(false)} created={(id) => { setShowNew(false); open(id); }} />}
    </div>
  );
}

function NewTicket({ close, created }: { close: () => void; created: (id: string) => void }) {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [cid, setCid] = useState("cus_0001");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const titleId = useId(), customerId = useId(), textId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => { api.customers().then(setCustomers).catch((e) => setError(errorText(e))); }, []);
  const escape = useRef(() => {});
  escape.current = () => { if (!busy) close(); };
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    dialog.current?.querySelector<HTMLElement>("select, textarea")?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") escape.current(); };
    window.addEventListener("keydown", onKey);
    return () => { window.removeEventListener("keydown", onKey); prev?.focus?.(); };
  }, []);
  const submit = async () => {
    if (busy || !text.trim()) return;
    setBusy(true); setError("");
    try {
      const t = await api.createTicket(cid, text.trim());
      await api.analyze(t.id);
      created(t.id);
    } catch (e) { setError(errorText(e)); setBusy(false); }
  };
  return (
    <div className="fixed inset-0 z-20 bg-black/30 flex items-center justify-center p-4" onClick={() => { if (!busy) close(); }}>
      <div ref={dialog} role="dialog" aria-modal="true" aria-labelledby={titleId}
        className="bg-white rounded-xl p-5 w-full max-w-lg space-y-3" onClick={(e) => e.stopPropagation()}>
        <h2 id={titleId} className="font-semibold">New ticket</h2>
        <label htmlFor={customerId} className="block text-xs font-semibold text-slate-700">Customer</label>
        <select id={customerId} value={cid} onChange={(e) => setCid(e.target.value)} className="w-full border rounded-md p-2 text-sm">
          {customers.map((c) => <option key={c.id} value={c.id}>{c.name} · {c.plan} · {c.platform} · {c.language}</option>)}
        </select>
        <label htmlFor={textId} className="block text-xs font-semibold text-slate-700">Customer message</label>
        <textarea id={textId} value={text} onChange={(e) => setText(e.target.value)} rows={4} maxLength={5000} placeholder="Customer message…" className="w-full border rounded-md p-2 text-sm" />
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <div className="flex justify-end gap-2">
          <button disabled={busy} onClick={close} className="px-3 py-1.5 text-sm disabled:opacity-50">Cancel</button>
          <button disabled={busy || !text.trim()} onClick={submit} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm disabled:opacity-50">
            {busy ? "Analyzing…" : "Send & analyze"}
          </button>
        </div>
      </div>
    </div>
  );
}
