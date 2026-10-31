import { usd, utc } from "../api";
import type { Analysis, Payment, Subscription, TicketDetail } from "../types";

type Rail = "store" | "web";
// The rail comes from the billing processor on the record, never from the customer's device.
const railOf = (processor: string | null | undefined): Rail =>
  /app_?store|apple|google|play/i.test(processor ?? "") ? "store" : "web";
const storeName = (processor: string | null | undefined) =>
  /google|play/i.test(processor ?? "") ? "Google Play" : /app_?store|apple/i.test(processor ?? "") ? "App Store" : "Store";

const PILL: Record<string, string> = {
  active: "bg-emerald-100 text-emerald-800", canceled: "bg-slate-200 text-slate-800", paused: "bg-sky-100 text-sky-800",
};

export default function PaymentRails({ c, a, verifiedAt }: { c: TicketDetail["customer"]; a: Analysis | null; verifiedAt: string | null }) {
  const subs: Subscription[] = c.subscriptions?.length ? c.subscriptions : c.subscription ? [c.subscription] : [];
  const latest = (rail: Rail, sub?: Subscription): Payment | undefined => {
    const own = sub?.id ? c.payments.filter((p) => p.subscription_id === sub.id) : [];
    const pool = own.length ? own : c.payments.filter((p) => railOf(p.processor) === rail);
    return [...pool].sort((x, y) => x.created_at.localeCompare(y.created_at)).at(-1);
  };
  const column = (rail: Rail, title: string) => {
    const mine = subs.filter((s) => railOf(s.processor) === rail);
    const pay = latest(rail, mine[0]);
    return (
      <div className="min-w-0 rounded-lg border border-slate-200 p-3 space-y-2">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-600">{title}</h4>
        {mine.length === 0 && <p className="text-xs text-slate-600">{subs.length ? "No subscription on this rail." : "Subscription not resolved (see reasons)."}</p>}
        {mine.map((s, i) => (
          <div key={s.id ?? i} className="text-sm space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs break-all">{s.id ?? "subscription"}</span>
              <span className={`rounded px-1.5 py-0.5 text-xs font-semibold ${PILL[s.status] ?? "bg-amber-100 text-amber-900"}`}>{s.status}</span>
            </div>
            <div className="text-xs text-slate-600">
              {rail === "store" && <>{storeName(s.processor)} · </>}{s.plan} · {usd(s.price)} {s.currency ?? "USD"}
              {s.discount_percent ? ` · −${s.discount_percent}%` : ""}
            </div>
            <div className="text-xs text-slate-600">
              {s.status === "canceled" ? "Access ends" : s.status === "paused" ? "Paused until" : "Renews"}{" "}
              {(s.status === "paused" ? s.pause_until : s.current_period_end)?.slice(0, 10) ?? "—"}
            </div>
          </div>
        ))}
        {pay && (
          <div className="border-t border-slate-100 pt-2 text-xs text-slate-600">
            {rail === "web" ? "Card payment" : "Store payment"} <span className="block font-mono text-slate-900 break-all">{pay.id}</span>
            <div>{usd(pay.amount)} · {pay.status} · {pay.created_at.slice(0, 10)}</div>
          </div>
        )}
      </div>
    );
  };
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4 space-y-3" aria-label="Payment rails">
      <h3 className="font-semibold">Payment rails</h3>
      <div className="grid gap-2 sm:grid-cols-2">
        {column("store", "App Store / Google Play")}
        {column("web", "Web card")}
      </div>
      <p className="text-xs text-slate-600">
        {a && verifiedAt ? <>Billing state verified at {utc(verifiedAt)} · re-checked on approve</> : "Billing state not verified yet — analyze the ticket first."}
      </p>
    </section>
  );
}
