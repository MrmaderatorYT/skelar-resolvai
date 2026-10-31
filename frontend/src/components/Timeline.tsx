import { actionLabel, utc } from "../api";
import type { TicketDetail } from "../types";

export default function Timeline({ events }: { events: TicketDetail["audit"] }) {
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="text-sm font-semibold mb-2">Audit log <span className="font-normal text-slate-600">· times in UTC</span></h3>
      {events.length === 0 && <p className="text-xs text-slate-600">No events yet.</p>}
      <ol className="text-xs space-y-1">
        {events.map((e, i) => (
          <li key={i} className={e.error ? "text-red-700" : ""}>
            <time dateTime={e.at} className="text-slate-600">{utc(e.at)}</time> · <b>{e.event}</b>
            {e.action && <> · {actionLabel(e.action)}</>}
            {e.by && <> · by {e.by}</>}
            {e.reason && <> · “{e.reason}”</>}
            {e.error && <> · {e.error}</>}
          </li>
        ))}
      </ol>
    </section>
  );
}
