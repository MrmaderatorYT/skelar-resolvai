import { useEffect, useState } from "react";
import { api } from "../api";

export default function EvalReport() {
  const [r, setR] = useState<Awaited<ReturnType<typeof api.evalReport>> | null>(null);
  useEffect(() => { api.evalReport().then(setR); }, []);
  if (!r) return <p>Loading…</p>;
  if (r.missing) return <p>No eval yet. Run <code>make eval</code> (or <code>make eval-baseline</code>).</p>;
  const block = (title: string, key: "defuse" | "baseline") => r[key] && (
    <section className="bg-white rounded-xl border border-slate-200 p-4 flex-1 min-w-0">
      <h2 className="font-semibold">{title}{r[key].model && <span className="text-slate-400 text-sm"> · {r[key].model}</span>}</h2>
      <p className={`text-sm font-semibold ${r[key].passed_gate ? "text-emerald-700" : "text-red-700"}`}>
        Safety gate: {r[key].passed_gate ? "PASS (0 unsafe refunds, 0 missed injections)" : "FAIL"}
      </p>
      <pre className="text-xs mt-2 overflow-x-auto">{r[key].report}</pre>
    </section>
  );
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Eval: 45 labelled tickets, 5 languages</h1>
      <p className="text-xs text-slate-500">Generated {r.generated_at}</p>
      <div className="flex gap-4 flex-col lg:flex-row">
        {block("Defuse", "defuse")}
        {block("Keyword bot (classic sentiment)", "baseline")}
      </div>
    </div>
  );
}
