import { useEffect, useState } from "react";
import { api, errorText } from "../api";
import type { PolicyRule as Rule } from "../types";

const strength = (source: string) => (source.match(/\(([ABCH])/)?.[1] ?? "H");
const BADGE: Record<string, string> = {
  A: "bg-emerald-100 text-emerald-800", B: "bg-sky-100 text-sky-800", C: "bg-amber-100 text-amber-800", H: "bg-slate-100 text-slate-700",
};
const LABEL: Record<string, string> = { A: "law / official", B: "report", C: "vendor data", H: "heuristic" };

const show = (v: unknown) => (typeof v === "object" && v !== null ? Object.entries(v).map(([k, x]) => `${k}: ${x}`).join(", ") : String(v));

export default function Policy() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [docs, setDocs] = useState<string[]>([]);
  const [doc, setDoc] = useState<{ name: string; markdown: string } | null>(null);
  const [error, setError] = useState("");
  const open = (name: string) => api.policyDoc(name).then(setDoc).catch((e) => setError(errorText(e)));
  useEffect(() => {
    api.policy().then((p) => { setRules(p.rules ?? []); setDocs(p.docs ?? []); }).catch((e) => setError(errorText(e)));
    open("RULES");
  }, []);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-xl font-bold">Policy</h1>
        <span className="text-sm text-slate-500">read-only · every number cites its evidence · edit defuse-policy.yml</span>
      </div>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="grid gap-4 lg:grid-cols-[1fr_1fr]">
        <section className="bg-white rounded-xl border border-slate-200 overflow-x-auto min-w-0">
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase text-slate-500 border-b">
              <tr>{["Rule", "Value", "Evidence"].map((h) => <th key={h} className="p-2">{h}</th>)}</tr>
            </thead>
            <tbody>
              {rules.map((r) => {
                const s = strength(r.source);
                return (
                  <tr key={r.key} className="border-b last:border-0 align-top">
                    <td className="p-2 font-mono text-xs">{r.key}</td>
                    <td className="p-2">{show(r.value)}</td>
                    <td className="p-2">
                      <span className={`rounded px-1.5 py-0.5 text-xs ${BADGE[s]}`}>{s} · {LABEL[s]}</span>
                      <button onClick={() => open("RULES")} className="block text-xs text-slate-600 hover:text-slate-900 mt-1 text-left">{r.source}</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </section>
        <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-2 min-w-0">
          <div className="flex flex-wrap gap-2">
            {docs.map((d) => (
              <button key={d} onClick={() => open(d)}
                className={`px-2 py-1 rounded text-sm ${doc?.name === d ? "bg-slate-900 text-white" : "border border-slate-300"}`}>{d}.md</button>
            ))}
          </div>
          <pre className="whitespace-pre-wrap break-words text-xs leading-relaxed max-h-[70vh] overflow-y-auto">{doc?.markdown ?? (error ? "" : "Loading…")}</pre>
        </section>
      </div>
    </div>
  );
}
