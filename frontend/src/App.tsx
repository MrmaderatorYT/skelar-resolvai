import { useEffect, useState } from "react";
import { api, loadSession, onSessionExpired, setSession } from "./api";
import Login from "./pages/Login";
import CustomerChat from "./pages/CustomerChat";
import EmbedSetup from "./pages/EmbedSetup";
import Dashboard from "./pages/Dashboard";
import EvalReport from "./pages/EvalReport";
import Policy from "./pages/Policy";
import Queue from "./pages/Queue";
import TicketView from "./pages/TicketView";
import SyntheticBanner from "./components/SyntheticBanner";
import type { Role } from "./types";

type Page = { name: "queue" } | { name: "inbox" } | { name: "ticket"; id: string } | { name: "dashboard" } | { name: "eval" } | { name: "policy" } | { name: "embed" };

export default function App() {
  const [page, setPage] = useState<Page>({ name: "queue" });
  const [role, setRole] = useState<Role | null>(() => loadSession()?.role ?? null);
  const [mode, setMode] = useState("");
  useEffect(() => { api.health().then((h) => setMode(h.llm_mode)).catch(() => setMode("offline")); }, []);
  useEffect(() => { onSessionExpired(() => { setRole(null); setPage({ name: "queue" }); }); }, []);
  useEffect(() => {
    const admin = role === "admin";
    document.documentElement.lang = admin ? "en" : "uk";
    document.title = admin ? "Defuse — support ops" : role === "customer" ? "calmly. — підтримка" : "calmly. — вхід";
  }, [role]);

  async function logout() { try { await api.logout(); } catch { /* session may already have expired */ } setSession(null); setRole(null); setPage({ name: "queue" }); }
  const embedded = new URLSearchParams(window.location.search).get("embed") === "1";
  if (!role) return <Login onLogin={setRole} />;
  if (role === "customer") return <CustomerChat logout={logout} />;
  if (embedded) return <div className="p-5 text-sm">У віджеті увійдіть як клієнт. <button onClick={logout} className="underline">Вийти</button></div>;
  const tab = (name: Page["name"], label: string) => (
    <button onClick={() => setPage({ name } as Page)} aria-current={page.name === name ? "page" : undefined}
      className={`px-3 py-1.5 rounded-md text-sm ${page.name === name ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-200"}`}>
      {label}
    </button>
  );

  return (
    <div className="min-h-screen">
      <header className="sm:sticky top-0 z-10 bg-white border-b border-slate-200">
        <SyntheticBanner compact />
        <div className="max-w-7xl mx-auto px-4 py-2 flex flex-wrap items-center gap-x-4 gap-y-2">
          <span className="font-bold text-lg">💣 Defuse</span>
          <nav aria-label="Main" className="flex flex-wrap gap-1">{tab("queue", "Queue")}{tab("inbox", "Inbox")}{tab("dashboard", "Dashboard")}{tab("eval", "Eval")}{tab("policy", "Policy")}{tab("embed", "Embed chat")}</nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-slate-500">LLM mode: <b>{mode}</b></span>
            <button onClick={logout} className="text-xs text-slate-600 underline">Sign out</button>
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-4 py-6">
        {page.name === "queue" && <Queue open={(id) => setPage({ name: "ticket", id })} />}
        {page.name === "inbox" && <Queue inbox open={(id) => setPage({ name: "ticket", id })} />}
        {page.name === "ticket" && <TicketView id={page.id} back={() => setPage({ name: "queue" })} />}
        {page.name === "dashboard" && <Dashboard />}
        {page.name === "eval" && <EvalReport />}
        {page.name === "policy" && <Policy />}
        {page.name === "embed" && <EmbedSetup />}
      </main>
    </div>
  );
}
