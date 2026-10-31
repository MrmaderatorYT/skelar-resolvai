import type { Analysis, Customer, Diagnostics, PolicyRule, Role, RootCause, Summary, TicketDetail, TicketRow } from "./types";

// The token lives in sessionStorage so a refresh keeps the session; storage can throw (private mode, blocked site data).
const SESSION_KEY = "defuse.session";
let token = "";
let expired: () => void = () => {};

export function loadSession(): { token: string; role: Role } | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? "null");
    if (s && typeof s.token === "string" && (s.role === "admin" || s.role === "customer")) { token = s.token; return s; }
  } catch { /* no stored session */ }
  return null;
}
export function setSession(s: { token: string; role: Role } | null) {
  token = s?.token ?? "";
  try { if (s) sessionStorage.setItem(SESSION_KEY, JSON.stringify(s)); else sessionStorage.removeItem(SESSION_KEY); } catch { /* memory only */ }
}
/** Called once when an authenticated request comes back 401, so the app can return to login. */
export function onSessionExpired(fn: () => void) { expired = fn; }

export class ApiError extends Error {
  constructor(public status: number, public detail: string) { super(`${status}: ${detail}`); }
}

export const CONFLICT_MESSAGE = "Billing state changed or the review expired. Reanalyze before approving.";
export function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 409) return e.detail ? `${CONFLICT_MESSAGE} (${e.detail})` : CONFLICT_MESSAGE;
    if (e.status === 401) return "Session expired. Sign in again.";
    if (e.status === 403) return "This account is not allowed to do that.";
    return e.detail || `Request failed (HTTP ${e.status}).`;
  }
  return e instanceof Error ? e.message : String(e);
}

async function req<T>(path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const authed = Boolean(token);
  if (authed) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(`/api${path}`, { method: body === undefined ? "GET" : "POST", headers,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  if (!res.ok) {
    const raw = await res.text().catch(() => "");
    let detail = raw;
    try { const j = JSON.parse(raw); detail = j.detail ?? j.message ?? j.error ?? raw; } catch { /* plain text body */ }
    if (res.status === 401 && authed) { setSession(null); expired(); }
    throw new ApiError(res.status, String(detail).slice(0, 300));
  }
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  return res.json();
}

export type ChatMessage = {
  id: string; text: string; status: string; reply: string | null; mode: string | null; scripted_demo: boolean; offer_pending: boolean;
  actions?: string[] | null;
};
export type EvalReportData = Record<string, { report: string; passed_gate: boolean | string; model?: string }> & { missing?: boolean; generated_at?: string; title?: string };

export const api = {
  login: (email: string, password: string) => req<{ token: string; role: Role }>("/auth/login", { email, password }),
  logout: () => req<void>("/auth/logout", {}),
  chat: () => req<ChatMessage[]>("/chat"),
  chatSuggestions: () => req<string[]>("/chat/suggestions"),
  sendChat: (text: string) => req<ChatMessage>("/chat", { text }),
  chatOffer: (id: string, action: "accept" | "decline") => req<ChatMessage>(`/chat/${encodeURIComponent(id)}/offer/${action}`, {}),
  humanReply: (id: string, text: string) => req<Analysis>(`/tickets/${encodeURIComponent(id)}/human-reply`, { text }),
  health: () => req<{ llm_mode: string; classifier: string; reply_model: string }>("/health"),
  tickets: () => req<TicketRow[]>("/tickets"),
  ticket: (id: string) => req<TicketDetail>(`/tickets/${encodeURIComponent(id)}`),
  analyze: (id: string, force = false) => req<Analysis>(`/tickets/${encodeURIComponent(id)}/analyze${force ? "?force=true" : ""}`, {}),
  analyzeAll: () => req<string[]>("/tickets/analyze-all", {}),
  approve: (id: string, analysis_id: string, reply_text?: string, action_hash?: string | null) =>
    req<Analysis>(`/tickets/${encodeURIComponent(id)}/approve`, { analysis_id, reply_text, ...(action_hash ? { action_hash } : {}) }),
  acceptOffer: (id: string) => req<Analysis>(`/tickets/${encodeURIComponent(id)}/offer/accept`, {}),
  declineOffer: (id: string) => req<Analysis>(`/tickets/${encodeURIComponent(id)}/offer/decline`, {}),
  reject: (id: string, reason: string) => req<Analysis>(`/tickets/${encodeURIComponent(id)}/reject`, { reason }),
  createTicket: (customer_id: string, text: string, channel = "chat") =>
    req<{ id: string }>("/tickets", { customer_id, text, channel }),
  customers: () => req<Customer[]>("/customers"),
  summary: () => req<Summary>("/analytics/summary"),
  diagnostics: () => req<Diagnostics>("/analytics/diagnostics"),
  rootCauses: () => req<RootCause[]>("/analytics/root-causes"),
  evalReport: () => req<EvalReportData>("/analytics/eval"),
  policy: () => req<{ rules: PolicyRule[]; docs: string[] }>("/policy"),
  policyDoc: (name: string) => req<{ name: string; markdown: string }>(`/policy/docs/${encodeURIComponent(name)}`),
  reset: () => req<{ ok: boolean }>("/demo/reset", {}),
};

/** Retention terms. Defaults mirror defuse-policy.yml; admins load the live values from /api/policy. */
export type OfferTerms = { discountPercent: number; discountMonths: number; pauseMonths: number };
export const DEFAULT_TERMS: OfferTerms = { discountPercent: 30, discountMonths: 3, pauseMonths: 2 };
export function termsFromRules(rules: PolicyRule[]): OfferTerms {
  const num = (key: string, fallback: number) => {
    const v = rules.find((r) => r.key === key)?.value;
    return typeof v === "number" && Number.isFinite(v) ? v : fallback;
  };
  return {
    discountPercent: num("retention.discount-percent", DEFAULT_TERMS.discountPercent),
    discountMonths: num("retention.discount-months", DEFAULT_TERMS.discountMonths),
    pauseMonths: num("retention.pause-months", DEFAULT_TERMS.pauseMonths),
  };
}
let termsPromise: Promise<OfferTerms> | null = null;
export function loadTerms(): Promise<OfferTerms> {
  termsPromise ??= api.policy().then((p) => termsFromRules(p.rules)).catch(() => { termsPromise = null; return DEFAULT_TERMS; });
  return termsPromise;
}

export function actionLabel(action: string, t: OfferTerms = DEFAULT_TERMS): string {
  const months = (n: number) => `${n} month${n === 1 ? "" : "s"}`;
  const dynamic: Record<string, string> = {
    pause: `Pause ${months(t.pauseMonths)}`,
    offer_discount: `Win-back offer: ${t.discountPercent}% off for ${months(t.discountMonths)}`,
    offer_pause: `Win-back offer: pause for ${months(t.pauseMonths)} instead of cancel`,
    apply_discount: `Apply ${t.discountPercent}% discount for ${months(t.discountMonths)}`,
  };
  return dynamic[action] ?? ACTION_LABEL[action] ?? action.replaceAll("_", " ");
}

const ACTION_LABEL: Record<string, string> = {
  cancel: "Cancel subscription",
  refund_full: "Full refund",
  refund_partial: "50% refund",
  refund_partial_goodwill: "Goodwill 50% refund",
  refund_duplicate: "Refund duplicate charge",
  extend_access_7d: "Add 7 free days",
  store_refund_instructions: "Send App Store / Google Play refund steps",
  store_cancel_instructions: "Send store cancel steps",
  evidence_pack: "Build dispute evidence pack",
  ask_clarification: "Ask a clarifying question",
  reply_only: "Reply only",
};

/** Plain-English labels for policy reason prefixes (the text before ':'). */
export const REASON_LABEL: Record<string, string> = {
  risk_not_calibrated: "Error risk is not calibrated yet, so money actions need a person",
  above_auto_refund_limit: "Refund is above the automatic limit",
  low_confidence: "The AI is unsure what the customer wants",
  refund_or_chargeback_history: "Customer has recent refunds or chargebacks",
  dispute_already_open: "A bank dispute is already open; refunding would pay twice",
  prompt_injection: "Message tries to instruct the system",
  identity_verification_required: "Identity must be verified first",
  ambiguous_subscriptions: "Customer has more than one contract; ask which one",
  unknown_billing_processor: "Payment provider is not supported",
  store_billing: "Bought through Apple or Google; only the store can refund or cancel",
  store_pause_requires_store_workflow: "Pause must be done in the app store",
  payment_not_refundable: "This payment cannot be refunded",
  already_refunded: "This payment was already refunded",
  provider_timeout: "Payment provider did not answer; result is being reconciled",
  duplicate_not_verified: "Duplicate charge could not be verified",
  outside_window_but_high_dispute_risk: "Outside refund window but high dispute risk; goodwill needs a person",
  outside_refund_window: "Outside the refund window",
  unknown_plan_period: "Plan period unknown; a person decides the amount",
  nothing_left_to_refund: "Paid period already used",
  retention_offer: "One optional win-back offer; the cancel already happened",
  retention_not_eligible: "No offer: customer not eligible",
  autonomy_gate_auto: "Autonomy gate allowed automatic handling",
  autonomy_gate_assist: "Autonomy gate requires a person to approve",
  autonomy_gate_human: "Autonomy gate hands this to a person",
};
export const reasonPrefix = (r: string) => r.split(":")[0].trim();
export const reasonLabel = (r: string) => REASON_LABEL[reasonPrefix(r)] ?? reasonPrefix(r).replaceAll("_", " ");

/** Plain labels for diagnostics.review_reasons codes. */
export const REVIEW_CODE_LABEL: Record<string, string> = {
  RISK_NOT_CALIBRATED: "Risk not calibrated", AMOUNT_OVER_LIMIT: "Amount over auto limit", LOW_CONFIDENCE: "Low AI confidence",
  REFUND_HISTORY: "Refund / chargeback history", DISPUTE_OPEN: "Bank dispute open", INJECTION: "Prompt injection",
  IDENTITY_UNCERTAIN: "Identity uncertain", PROVIDER_UNSUPPORTED: "Provider unsupported", CROSS_RAIL: "Store billing (other rail)",
  PAYMENT_NOT_REFUNDABLE: "Payment not refundable", ALREADY_REFUNDED: "Already refunded", AMBIGUOUS_SUBSCRIPTION: "Ambiguous subscription",
  PROVIDER_TIMEOUT: "Provider timeout", GATE_ASSIST: "Autonomy gate: approve", GATE_HUMAN: "Autonomy gate: human", OTHER: "Other",
};

/** Display vocabulary for the internal decision modes. */
export const ROUTE: Record<string, "AUTO" | "REVIEW" | "INVESTIGATE"> = { auto: "AUTO", suggest: "REVIEW", escalate: "INVESTIGATE" };

export const usd = (n: number | null | undefined) => (typeof n === "number" && Number.isFinite(n) ? `$${n.toFixed(2)}` : "—");
export const utc = (iso: string | null | undefined) => {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : `${d.toISOString().slice(0, 10)} ${d.toISOString().slice(11, 19)} UTC`;
};
