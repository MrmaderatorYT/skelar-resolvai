import type { Analysis, Customer, RootCause, Summary, TicketDetail, TicketRow } from "./types";

async function req<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`/api${path}`, body === undefined ? undefined : {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`);
  return res.json();
}

export const api = {
  health: () => req<{ llm_mode: string; classifier: string; reply_model: string }>("/health"),
  tickets: () => req<TicketRow[]>("/tickets"),
  ticket: (id: string) => req<TicketDetail>(`/tickets/${id}`),
  analyze: (id: string) => req<Analysis>(`/tickets/${id}/analyze`, {}),
  analyzeAll: () => req<string[]>("/tickets/analyze-all", {}),
  approve: (id: string, reply_text?: string) => req<Analysis>(`/tickets/${id}/approve`, { reply_text }),
  acceptOffer: (id: string) => req<Analysis>(`/tickets/${id}/offer/accept`, {}),
  declineOffer: (id: string) => req<Analysis>(`/tickets/${id}/offer/decline`, {}),
  reject: (id: string, reason: string) => req<Analysis>(`/tickets/${id}/reject`, { reason }),
  createTicket: (customer_id: string, text: string, channel = "chat") =>
    req<{ id: string }>("/tickets", { customer_id, text, channel }),
  customers: () => req<Customer[]>("/customers"),
  summary: () => req<Summary>("/analytics/summary"),
  rootCauses: () => req<RootCause[]>("/analytics/root-causes"),
  evalReport: () => req<Record<string, { report: string; passed_gate: boolean; model?: string }> & { missing?: boolean; generated_at?: string }>("/analytics/eval"),
  reset: () => req<{ ok: boolean }>("/demo/reset", {}),
};

export const ACTION_LABEL: Record<string, string> = {
  cancel: "Cancel subscription",
  refund_full: "Full refund",
  refund_partial: "50% refund",
  refund_partial_goodwill: "Goodwill 50% refund",
  refund_duplicate: "Refund duplicate charge",
  pause: "Pause 2 months",
  offer_discount: "Retention offer: 25% off for 2 months",
  offer_pause: "Retention offer: pause for 2 months",
  apply_discount: "Apply 25% discount",
  extend_access_7d: "Add 7 free days",
  store_refund_instructions: "Send App Store / Google Play refund steps",
  store_cancel_instructions: "Send store cancel steps",
  evidence_pack: "Build dispute evidence pack",
  ask_clarification: "Ask a clarifying question",
  reply_only: "Reply only",
};

export const usd = (n: number) => `$${n.toFixed(2)}`;
