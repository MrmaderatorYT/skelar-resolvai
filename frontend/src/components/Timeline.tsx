import { ACTION_LABEL } from "../api";
import type { TicketDetail } from "../types";

export default function Timeline({ events }: { events: TicketDetail["audit"] }) {
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="text-sm font-semibold mb-2">Audit log</h3>
      <ol className="text-xs space-y-1">
        {events.map((e, i) => (
          <li key={i} className={e.error ? "text-red-700" : ""}>
            <span className="text-slate-400">{e.at.slice(11, 19)}</span> · <b>{e.event}</b>
            {e.action && <> · {ACTION_LABEL[e.action] ?? e.action}</>}
            {e.by && <> · by {e.by}</>}
            {e.error && <> · {e.error}</>}
          </li>
        ))}
      </ol>
    </section>
  );
}
