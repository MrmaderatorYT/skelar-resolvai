import type { Band } from "../types";

const STYLE: Record<Band, string> = {
  high: "bg-red-100 text-red-800 ring-red-300",
  medium: "bg-amber-100 text-amber-800 ring-amber-300",
  low: "bg-emerald-50 text-emerald-700 ring-emerald-200",
};

export default function RiskBadge({ band, score, large }: { band: Band | null; score?: number | null; large?: boolean }) {
  if (!band) return <span className="text-slate-600 text-xs" aria-label="not analyzed">—</span>;
  return (
    <span className={`inline-flex items-center gap-1 rounded-full ring-1 font-medium ${STYLE[band]} ${large ? "px-3 py-1 text-base" : "px-2 py-0.5 text-xs"}`}>
      {band} risk{score != null && <span className="opacity-70">· {score.toFixed(2)}</span>}
    </span>
  );
}
