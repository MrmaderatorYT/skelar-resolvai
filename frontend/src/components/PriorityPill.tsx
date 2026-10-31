const STYLE: Record<string, string> = {
  P1: "bg-red-600 text-white", P2: "bg-amber-500 text-white", P3: "bg-slate-200 text-slate-700", P4: "bg-slate-100 text-slate-700",
};

export default function PriorityPill({ p }: { p: string | null }) {
  if (!p) return <span className="text-slate-600 text-xs">new</span>;
  return <span className={`rounded px-1.5 py-0.5 text-xs font-bold ${STYLE[p]}`}>{p}</span>;
}
