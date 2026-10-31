import { useEffect, useState } from "react";
import { api, errorText, utc, type EvalReportData } from "../api";

const passed = (v: unknown) => v === true || v === "true" || v === "True";

export default function EvalReport() {
  const [r, setR] = useState<EvalReportData | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { api.evalReport().then(setR).catch((e) => setError(errorText(e))); }, []);
  if (error) return <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">Could not load the eval report: {error}</p>;
  if (!r) return <p role="status">Loading…</p>;
  if (r.missing) return <p>No eval yet. Run <code>make eval</code> (or <code>make eval-baseline</code>).</p>;
  const block = (title: string, key: "defuse" | "baseline") => r[key] && (
    <section className="bg-white rounded-xl border border-slate-200 p-4 flex-1 min-w-0">
      <h2 className="font-semibold">{title}{r[key].model && <span className="text-slate-600 text-sm font-normal"> · {r[key].model}</span>}</h2>
      <p className={`text-sm font-semibold ${passed(r[key].passed_gate) ? "text-emerald-700" : "text-red-700"}`}>
        Safety gate: {passed(r[key].passed_gate) ? "PASS (0 unsafe refunds, 0 missed injections on this set)" : "FAIL"}
      </p>
      <pre className="text-xs mt-2 overflow-x-auto">{r[key].report}</pre>
    </section>
  );
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">{typeof r.title === "string" && r.title ? r.title : "Offline evaluation"}</h1>
      <p className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950">
        Caveat: synthetic, n=45 offline gold replay; not a production error rate.
      </p>
      {r.generated_at && <p className="text-xs text-slate-600">Generated {utc(r.generated_at)}</p>}
      <div className="flex gap-4 flex-col lg:flex-row">
        {block("Defuse", "defuse")}
        {block("Keyword bot (classic sentiment)", "baseline")}
      </div>
    </div>
  );
}
