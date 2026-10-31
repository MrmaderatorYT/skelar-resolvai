import type { Features } from "../types";
import { SIGNAL } from "./SignalChips";

// Builds React nodes, never HTML strings: ticket text is untrusted.
export default function HighlightedText({ text, evidence }: { text: string; evidence: Features["evidence"] }) {
  const marks: { start: number; end: number; signal: keyof typeof SIGNAL }[] = [];
  for (const e of evidence) {
    const start = e.quote ? text.indexOf(e.quote) : -1;
    if (start >= 0 && !marks.some((m) => start < m.end && start + e.quote.length > m.start)) {
      marks.push({ start, end: start + e.quote.length, signal: e.signal });
    }
  }
  marks.sort((a, b) => a.start - b.start);
  const out: React.ReactNode[] = [];
  let pos = 0;
  marks.forEach((m, i) => {
    out.push(text.slice(pos, m.start));
    out.push(<mark key={i} title={SIGNAL[m.signal].label} className={`${SIGNAL[m.signal].mark} rounded px-0.5`}>{text.slice(m.start, m.end)}</mark>);
    pos = m.end;
  });
  out.push(text.slice(pos));
  return <p className="whitespace-pre-wrap text-lg leading-relaxed">{out}</p>;
}
