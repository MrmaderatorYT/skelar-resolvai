import { usd } from "../api";
import type { TicketDetail } from "../types";

export default function CustomerPanel({ c }: { c: TicketDetail["customer"] }) {
  const row = (k: string, v: React.ReactNode, warn = false) => (
    <div className="flex justify-between gap-2 py-1 border-b border-slate-100 text-sm">
      <span className="text-slate-500">{k}</span><span className={warn ? "font-semibold text-red-700" : "font-medium"}>{v}</span>
    </div>
  );
  const lp = c.context.last_payment;
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="font-semibold mb-2">{c.name} <span className="text-slate-400 text-sm">· {c.country}</span></h3>
      {row("Plan", `${c.subscription.plan} · ${usd(c.subscription.price)}`)}
      {row("Subscription", c.subscription.status + (c.subscription.discount_percent ? ` · −${c.subscription.discount_percent}%` : ""))}
      {row("Came from", `${c.acquisition_channel ?? "—"} · ${c.funnel ?? "—"}`)}
      {c.quiz_goal && row("Quiz goal", c.quiz_goal)}
      {row("With us", `${c.context.tenure_months} mo · ${c.context.paid_renewals} paid renewals`)}
      {row("Offers (12 mo) / linked accounts", `${c.context.offers_12m} / ${c.context.identity_offers_12m}`,
        c.context.offers_12m + c.context.identity_offers_12m > 0)}
      {c.context.in_holdout && row("Retention holdout", "yes — no offers", true)}
      {row("Billing", c.context.billing_processor)}
      {row("LTV", usd(c.ltv_usd))}
      {row("Last charge", `${usd(lp.amount)} · ${lp.days_ago}d ago · ${lp.status}`)}
      {row("Sessions since charge", c.usage.sessions_since_last_charge)}
      {row("Abandoned cancel flow", c.usage.cancel_flow_abandoned, c.usage.cancel_flow_abandoned > 0)}
      {row("Contacts (30d)", c.contacts_last_30d, c.contacts_last_30d > 0)}
      {row("Refunds (180d) / chargebacks", `${c.context.refunds_last_180d} / ${c.context.chargebacks_lifetime}`,
        c.context.refunds_last_180d + c.context.chargebacks_lifetime > 0)}
      <h4 className="text-xs uppercase text-slate-400 mt-3 mb-1">Payments</h4>
      <ul className="text-xs space-y-0.5">
        {c.payments.slice(-5).reverse().map((p) => (
          <li key={p.id} className="flex justify-between"><span>{p.created_at.slice(0, 10)} · {p.type}</span><span>{usd(p.amount)} · {p.status}</span></li>
        ))}
      </ul>
    </section>
  );
}
