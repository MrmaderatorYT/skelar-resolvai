import { useEffect, useState } from "react";
import { api } from "../api";
import PriorityPill from "../components/PriorityPill";
import RiskBadge from "../components/RiskBadge";
import SignalChips from "../components/SignalChips";
import type { Customer, TicketRow } from "../types";

const STATUS: Record<string, string> = {
  open: "bg-slate-100 text-slate-600", resolved: "bg-emerald-100 text-emerald-800",
  needs_approval: "bg-amber-100 text-amber-800", escalated: "bg-red-100 text-red-800",
};

export default function Queue({ open }: { open: (id: string) => void }) {
  const [rows, setRows] = useState<TicketRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showNew, setShowNew] = useState(false);
  const load = () => api.tickets().then(setRows).catch((e) => setError(String(e)));
  useEffect(() => { load(); }, []);

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("");
    try { await fn(); await load(); } catch (e) { setError(String(e)); } finally { setBusy(false); }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h1 className="text-xl font-bold">Support queue</h1>
        <span className="text-sm text-slate-500">{rows.filter((r) => r.status === "open").length} open</span>
        <div className="ml-auto flex gap-2">
          <button disabled={busy} onClick={() => setShowNew(true)} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-white">New ticket</button>
          <button disabled={busy} onClick={() => run(api.reset)} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-white">Reset demo</button>
          <button disabled={busy} onClick={() => run(api.analyzeAll)} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm font-semibold disabled:opacity-50">
            {busy ? "Analyzing…" : "Analyze all"}
          </button>
        </div>
      </div>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <div className="bg-white rounded-xl border border-slate-200 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-slate-500 border-b">
            <tr>{["Prio", "Risk", "Signals", "Customer", "Message", "Lang", "Status"].map((h) => <th key={h} className="p-2">{h}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} onClick={() => open(r.id)} className={`border-b last:border-0 cursor-pointer hover:bg-slate-50 ${r.priority === "P1" ? "bg-red-50/40" : ""}`}>
                <td className="p-2"><PriorityPill p={r.priority} /></td>
                <td><RiskBadge band={r.risk_band} /></td>
                <td className="max-w-48"><SignalChips signals={r.signals} /></td>
                <td className="whitespace-nowrap pr-2">{r.customer_name}</td>
                <td className="text-slate-600 pr-2">{r.text_preview}{r.text.length > 90 && "…"}</td>
                <td className="uppercase text-xs">{r.language}</td>
                <td><span className={`rounded px-1.5 py-0.5 text-xs whitespace-nowrap ${STATUS[r.status]}`}>{r.status.replace("_", " ")}</span></td>
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
  useEffect(() => { api.customers().then(setCustomers); }, []);
  const submit = async () => {
    const t = await api.createTicket(cid, text);
    await api.analyze(t.id);
    created(t.id);
  };
  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center p-4" onClick={close}>
      <div className="bg-white rounded-xl p-5 w-full max-w-lg space-y-3" onClick={(e) => e.stopPropagation()}>
        <h2 className="font-semibold">New ticket</h2>
        <select value={cid} onChange={(e) => setCid(e.target.value)} className="w-full border rounded-md p-2 text-sm">
          {customers.map((c) => <option key={c.id} value={c.id}>{c.name} · {c.plan} · {c.platform} · {c.language}</option>)}
        </select>
        <textarea value={text} onChange={(e) => setText(e.target.value)} rows={4} placeholder="Customer message…" className="w-full border rounded-md p-2 text-sm" />
        <div className="flex justify-end gap-2">
          <button onClick={close} className="px-3 py-1.5 text-sm">Cancel</button>
          <button disabled={!text.trim()} onClick={submit} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm disabled:opacity-50">Send & analyze</button>
        </div>
      </div>
    </div>
  );
}
