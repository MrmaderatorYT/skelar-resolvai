import { usd } from "../api";
import type { TicketDetail } from "../types";

export default function CustomerPanel({ c }: { c: TicketDetail["customer"] }) {
  const row = (k: string, v: React.ReactNode, warn = false) => (
    <div className="flex justify-between gap-2 py-1 border-b border-slate-100 text-sm">
      <span className="text-slate-600">{k}</span><span className={`text-right ${warn ? "font-semibold text-red-700" : "font-medium"}`}>{v}</span>
    </div>
  );
  const ctx = c.context, sub = c.subscription, lp = ctx?.last_payment;
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="font-semibold mb-2">{c.name} <span className="text-slate-600 text-sm font-normal">· {c.country}</span></h3>
      {sub && row("Plan", `${sub.plan} · ${usd(sub.price)}`)}
      {sub && row("Subscription", sub.status + (sub.discount_percent ? ` · −${sub.discount_percent}%` : ""))}
      {row("Came from", `${c.acquisition_channel ?? "—"} · ${c.funnel ?? "—"}`)}
      {c.quiz_goal && row("Quiz goal", c.quiz_goal)}
      {ctx ? <>
        {row("With us", `${ctx.tenure_months} mo · ${ctx.paid_renewals} paid renewals`)}
        {row("Offers (12 mo) / linked accounts", `${ctx.offers_12m} / ${ctx.identity_offers_12m}`, ctx.offers_12m + ctx.identity_offers_12m > 0)}
        {ctx.in_holdout && row("Retention holdout", "yes — no offers", true)}
        {row("Billing", ctx.billing_processor)}
        {lp && row("Last charge", `${usd(lp.amount)} · ${lp.days_ago}d ago · ${lp.status}`)}
        {row("Refunds (180d) / chargebacks", `${ctx.refunds_last_180d} / ${ctx.chargebacks_lifetime}`, ctx.refunds_last_180d + ctx.chargebacks_lifetime > 0)}
      </> : <p className="my-2 rounded bg-amber-50 p-2 text-xs text-amber-900">Billing context withheld: the subscription is ambiguous, so no single contract is trusted. Ask the customer which one they mean.</p>}
      {(c.linked_subscriptions?.length ?? 0) > 1 && <><h4 className="text-xs uppercase text-slate-400 mt-3 mb-1">Linked subscriptions</h4>{c.linked_subscriptions?.map(s => <div key={s.id} className="text-xs text-slate-600 py-1">{s.processor} · {s.plan} · {s.status} <span className="text-slate-400">({s.id})</span></div>)}</>}
      {row("LTV", usd(c.ltv_usd))}
      {c.usage && row("Sessions since charge", c.usage.sessions_since_last_charge)}
      {c.usage && row("Abandoned cancel flow", c.usage.cancel_flow_abandoned, c.usage.cancel_flow_abandoned > 0)}
      {row("Contacts (30d)", c.contacts_last_30d, c.contacts_last_30d > 0)}
      <h4 className="text-xs uppercase text-slate-600 mt-3 mb-1">Payments</h4>
      <ul className="text-xs space-y-0.5">
        {c.payments.length === 0 && <li className="text-slate-600">No payments.</li>}
        {c.payments.slice(-5).reverse().map((p) => (
          <li key={p.id} className="flex justify-between gap-2"><span>{p.created_at.slice(0, 10)} · {p.type} · {p.processor}</span><span>{usd(p.amount)} · {p.status}</span></li>
        ))}
      </ul>
    </section>
  );
}
