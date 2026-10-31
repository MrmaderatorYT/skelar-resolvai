import { useEffect, useId, useState, type FormEvent } from "react";
import { api, DEFAULT_TERMS, type ChatMessage } from "../api";

const STATUS: Record<string, { label: string; cls: string }> = {
  open: { label: "Обробляється", cls: "bg-slate-100 text-slate-700" },
  needs_approval: { label: "Передано фахівцю", cls: "bg-amber-100 text-amber-900" },
  escalated: { label: "Потрібна перевірка", cls: "bg-red-100 text-red-800" },
  resolved: { label: "Виконано", cls: "bg-emerald-100 text-emerald-800" },
  offer_sent: { label: "Підписку скасовано · є пропозиція", cls: "bg-sky-100 text-sky-800" },
};
const CANCEL_TEXT = "Скасуйте, будь ласка, мою підписку.";
// Customers cannot read /api/policy, so the chat uses the policy defaults for offer wording.
const T = DEFAULT_TERMS;

/** Which win-back offer is open: from the decision actions when the server sends them, else from the reply text. */
function offerKind(row: ChatMessage): "discount" | "pause" | null {
  if (row.actions?.includes("offer_discount")) return "discount";
  if (row.actions?.includes("offer_pause")) return "pause";
  if (/пауз|pause/i.test(row.reply ?? "")) return "pause";
  if (/знижк|discount|%/i.test(row.reply ?? "")) return "discount";
  return null;
}

export default function CustomerChat({ logout }: { logout: () => void }) {
  const [rows, setRows] = useState<ChatMessage[]>([]), [suggestions, setSuggestions] = useState<string[]>([]);
  const [text, setText] = useState(""), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const inputId = useId();
  const refresh = () => api.chat().then((r) => { setRows(r); }).catch(() => setError("Не вдалося отримати історію звернень."));
  useEffect(() => { refresh(); api.chatSuggestions().then(setSuggestions).catch(() => {}); const timer = window.setInterval(refresh, 3000); return () => window.clearInterval(timer); }, []);
  async function deliver(message: string) {
    if (!message.trim() || busy) return;
    setBusy(true); setError("");
    try { await api.sendChat(message.trim()); setText(""); await refresh(); }
    catch { setError("Не вдалося обробити повідомлення. Спробуйте ще раз."); }
    finally { setBusy(false); }
  }
  function send(e: FormEvent) { e.preventDefault(); deliver(text); }
  async function offer(id: string, action: "accept" | "decline") {
    if (busy) return;
    setBusy(true); setError("");
    try { await api.chatOffer(id, action); await refresh(); } catch { setError("Не вдалося обробити вибір."); } finally { setBusy(false); }
  }
  return (
    <div className="min-h-screen bg-emerald-50/50">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between p-5">
          <b className="text-xl text-emerald-900">calmly.</b>
          <button onClick={logout} className="text-sm text-slate-600 underline">Вийти</button>
        </div>
      </header>
      <main className="mx-auto max-w-3xl p-4 sm:p-5">
        <h1 className="mt-4 text-2xl sm:text-3xl font-bold text-emerald-950">Ми поруч, щоб допомогти</h1>
        <p className="mt-2 text-sm text-slate-600">Напишіть про підписку чи оплату — ШІ-помічник перевірить звернення. Рішення щодо повернення коштів підтверджує фахівець.</p>
        <section className="mt-6 rounded-2xl border bg-white p-4 sm:p-5 shadow-sm">
          <div className="min-h-60 space-y-4" aria-live="polite">
            {rows.length === 0 && <p className="text-sm text-slate-600">Опишіть, що сталося, і ми почнемо розмову.</p>}
            {rows.map((row) => {
              const st = STATUS[row.status] ?? { label: "Обробляється", cls: STATUS.open.cls };
              const kind = offerKind(row);
              return (
                <div key={row.id} className="space-y-2">
                  <div className="ml-auto max-w-[85%] whitespace-pre-wrap break-words rounded-2xl bg-emerald-800 p-3 text-sm text-white">{row.text}</div>
                  <div className="mr-auto max-w-[85%] whitespace-pre-wrap break-words rounded-2xl bg-slate-100 p-3 text-sm">
                    {row.reply || "Обробляємо звернення..."}
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${st.cls}`}>{st.label}</span>
                      {row.scripted_demo && <span className="text-xs font-semibold text-amber-800">Демо-сценарій — готові мітки</span>}
                    </div>
                  </div>
                  {row.offer_pending && (
                    <div className="mr-auto max-w-[85%] space-y-2 rounded-2xl border border-sky-200 bg-sky-50 p-3 text-sm">
                      <p>Вашу підписку вже скасовано. Якщо бажаєте, можете повернутися на особливих умовах — це необов’язково.</p>
                      <div className="flex flex-wrap gap-2">
                        <button disabled={busy} onClick={() => offer(row.id, "accept")} className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">
                          {kind === "pause" ? `Поставити на паузу на ${T.pauseMonths} міс.` : kind === "discount" ? `Повернутися зі знижкою ${T.discountPercent}% на ${T.discountMonths} міс.` : "Прийняти пропозицію"}
                        </button>
                        <button disabled={busy} onClick={() => offer(row.id, "decline")} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm disabled:opacity-50">Ні, дякую</button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
            {busy && <p role="status" className="text-xs text-slate-600">Помічник відповідає...</p>}
          </div>
          {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
          {suggestions.length > 0 && (
            <div className="mt-5">
              <p className="mb-2 text-xs text-slate-600">Приклади для демо (готові мітки):</p>
              {suggestions.map((s, i) => <button key={i} onClick={() => setText(s)} className="mb-2 block max-w-full truncate rounded border px-3 py-2 text-left text-xs text-slate-700">{s}</button>)}
            </div>
          )}
          <div className="mt-5">
            <button disabled={busy} onClick={() => deliver(CANCEL_TEXT)} className="rounded-full border border-emerald-800 px-3 py-1.5 text-sm font-semibold text-emerald-900 hover:bg-emerald-50 disabled:opacity-50">
              Скасувати підписку
            </button>
          </div>
          <form onSubmit={send} className="mt-3 flex gap-2">
            <label htmlFor={inputId} className="sr-only">Ваше повідомлення</label>
            <textarea id={inputId} value={text} onChange={(e) => setText(e.target.value)} placeholder="Напишіть повідомлення..." maxLength={5000} rows={2} className="min-w-0 flex-1 rounded-xl border p-3 text-sm" />
            <button disabled={busy || !text.trim()} className="rounded-xl bg-emerald-800 px-4 text-sm font-semibold text-white disabled:opacity-40">Надіслати</button>
          </form>
          <p className="mt-3 text-xs text-slate-600">Демодані; рішення щодо коштів перевіряє політика та, за потреби, фахівець.</p>
        </section>
      </main>
    </div>
  );
}
