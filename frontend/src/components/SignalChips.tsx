import type { Signal } from "../types";

export const SIGNAL: Record<Signal | "goal", { label: string; chip: string; mark: string }> = {
  sarcasm: { label: "🙃 sarcasm", chip: "bg-purple-100 text-purple-800", mark: "bg-purple-200" },
  hidden_aggression: { label: "🫥 hidden aggression", chip: "bg-orange-100 text-orange-800", mark: "bg-orange-200" },
  explicit_aggression: { label: "😠 aggression", chip: "bg-red-100 text-red-800", mark: "bg-red-200" },
  dispute_signal: { label: "🏦 dispute risk", chip: "bg-rose-100 text-rose-800", mark: "bg-rose-200" },
  injection: { label: "🛡 injection", chip: "bg-slate-800 text-white", mark: "bg-slate-300" },
  goal: { label: "🎯 goal", chip: "bg-sky-100 text-sky-800", mark: "bg-sky-100" },
};

export default function SignalChips({ signals }: { signals: Signal[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {signals.map((s) => <span key={s} className={`rounded px-1.5 py-0.5 text-xs ${SIGNAL[s].chip}`}>{SIGNAL[s].label}</span>)}
    </div>
  );
}
