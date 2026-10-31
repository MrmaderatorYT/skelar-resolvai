import type { Analysis, Customer, RootCause, Summary, TicketDetail, TicketRow } from "./types";

let token = "";
export function setSessionToken(value: string) { token = value; }
async function req<T>(path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(`/api${path}`, { method: body === undefined ? "GET" : "POST", headers,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`);
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  return res.json();
}

export type ChatMessage = { id: string; text: string; status: string; reply: string | null; mode: string | null; scripted_demo: boolean; offer_pending: boolean };
export const api = {
  login: (email: string, password: string) => req<{token: string; role: "customer" | "admin"}>("/auth/login", {email, password}),
  logout: () => req<void>("/auth/logout", {}),
  chat: () => req<ChatMessage[]>("/chat"),
  chatSuggestions: () => req<string[]>("/chat/suggestions"),
  sendChat: (text: string) => req<ChatMessage>("/chat", {text}),
  chatOffer: (id: string, action: "accept" | "decline") => req<ChatMessage>(`/chat/${id}/offer/${action}`, {}),
  humanReply: (id: string, text: string) => req<Analysis>(`/tickets/${id}/human-reply`, {text}),
  health: () => req<{ llm_mode: string; classifier: string; reply_model: string }>("/health"),
  tickets: () => req<TicketRow[]>("/tickets"),
  ticket: (id: string) => req<TicketDetail>(`/tickets/${id}`),
  analyze: (id: string, force = false) => req<Analysis>(`/tickets/${id}/analyze${force ? "?force=true" : ""}`, {}),
  analyzeAll: () => req<string[]>("/tickets/analyze-all", {}),
  approve: (id: string, analysis_id: string, reply_text?: string) => req<Analysis>(`/tickets/${id}/approve`, { analysis_id, reply_text }),
  acceptOffer: (id: string) => req<Analysis>(`/tickets/${id}/offer/accept`, {}),
  declineOffer: (id: string) => req<Analysis>(`/tickets/${id}/offer/decline`, {}),
  reject: (id: string, reason: string) => req<Analysis>(`/tickets/${id}/reject`, { reason }),
  createTicket: (customer_id: string, text: string, channel = "chat") =>
    req<{ id: string }>("/tickets", { customer_id, text, channel }),
  customers: () => req<Customer[]>("/customers"),
  summary: () => req<Summary>("/analytics/summary"),
  diagnostics: () => req<{resolved: {auto:number; assisted:number; human:number}; reasons: Record<string,number>; synthetic_segments: {segment:string;count:number}[]}>("/analytics/diagnostics"),
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
