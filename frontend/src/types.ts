export type Band = "low" | "medium" | "high";
export type Mode = "auto" | "suggest" | "escalate";
export type Status = "open" | "resolved" | "needs_approval" | "escalated" | "offer_sent";
export type Signal = "sarcasm" | "hidden_aggression" | "explicit_aggression" | "dispute_signal" | "injection";

export interface TicketRow {
  id: string; customer_id: string; customer_name: string; channel: string; language: string;
  created_at: string; text: string; text_preview: string; status: Status;
  priority: string | null; risk_band: Band | null; risk_score: number | null; mode: Mode | null;
  signals: Signal[];
}

export interface Features {
  intent: string; true_goal: string; sarcasm: boolean; hidden_aggression: boolean;
  explicit_aggression: boolean; dispute_signal: boolean; injection: boolean;
  sentiment: string; language: string; confidence: number;
  evidence: { quote: string; signal: Signal | "goal" }[];
}

export interface Decision {
  actions: string[]; mode: Mode; refund_amount: number; risk_score: number;
  risk_band: Band; priority: string; reasons: string[];
}

export interface Analysis {
  autonomy?: { p_err: number; impact_usd: number; expected_loss_usd: number; level: "AUTO" | "ASSIST" | "HUMAN"; source: string };
  ticket_id: string; analysis_id: string; features: Features; decision: Decision; reply: string; status: Status;
  latency_ms: number; injection_guard: { flagged: boolean; patterns: string[] };
  money: { amount_at_risk: number; fees_at_risk: number; p_dispute: number; expected_fees_avoided: number };
  executed: Record<string, unknown>[];
}

export interface Payment { id: string; amount: number; type: string; status: string; processor: string; created_at: string; subscription_id?: string | null }

export interface TicketDetail {
  ticket: { id: string; text: string; channel: string; language: string; created_at: string; status: Status; customer_id: string };
  customer: {
    id: string; name: string; country: string; platform: string; ltv_usd: number; contacts_last_30d: number;
    acquisition_channel: string | null; funnel: string | null; quiz_goal: string | null; offers_12m: number;
    subscription: { plan: string; price: number; status: string; current_period_end: string; discount_percent: number | null; pause_until: string | null };
    linked_subscriptions?: { id: string; plan: string; status: string; processor: string }[];
    usage: { sessions_since_last_charge: number; cancel_flow_abandoned: number; streak_days: number };
    payments: Payment[];
    context: { last_payment: { amount: number; status: string; days_ago: number }; refunds_last_180d: number; chargebacks_lifetime: number;
      tenure_months: number; paid_renewals: number; offers_12m: number; identity_offers_12m: number; in_holdout: boolean; billing_processor: string;
      cross_rail?: { store_subscription_id: string; store_status: string; card_subscription_id: string; card_status: string; card_payment_id: string } | null };
  };
  analysis: Analysis | null;
  audit: { at: string; event: string; action?: string; by?: string; reasons?: string[]; error?: string; amount?: number }[];
  evidence: unknown;
}

export interface Summary {
  tickets_total: number; analyzed: number; by_status: Record<string, number>; auto_resolved_pct: number;
  refunded_usd: number; gross_exposure_usd: number; expected_fees_avoided_usd: number;
  expected_chargebacks_avoided: number; high_risk_tickets: number; sarcasm_detected: number;
  injections_blocked: number; ai_median_latency_ms: number | null;
  retention: { offers_sent: number; offers_accepted: number; offers_declined: number; not_eligible: Record<string, number>;
    acceptance_rate: number | null; holdout_customers: number; discount_cost_usd: number };
  money_settings: { dispute_fee_usd: number; scheme_fee_per_dispute_usd: number; merchant_region: string; vamp_threshold: number; note: string };
  baseline: { label: string; tickets: number; median_first_response_hours: number;
    median_resolution_hours: number; chargebacks_filed: number; p_dispute_by_band: Record<Band, number> };
}

export interface RootCause { root_cause: string; count: number; share: number; avg_risk: number; chargeback_rate: number; recommended_fix: string }
export interface Customer { id: string; name: string; platform: string; language: string; plan: string }
