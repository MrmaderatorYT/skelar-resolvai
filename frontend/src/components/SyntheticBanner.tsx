export default function SyntheticBanner({ compact = false }: { compact?: boolean }) {
  return <div role="note" className={`bg-amber-100 text-amber-950 border-amber-300 font-semibold ${compact ? "text-xs px-4 py-1 border-b" : "rounded-xl border px-4 py-2 text-sm"}`}>
    SYNTHETIC DATA · risk UNVALIDATED <span className="font-normal">— demo fixtures; no measured error rate; money actions need human approval.</span>
  </div>;
}
