import { useEffect, useState } from "react";
import { api, setSessionToken } from "./api";
import Login from "./pages/Login";
import CustomerChat from "./pages/CustomerChat";
import EmbedSetup from "./pages/EmbedSetup";
import Dashboard from "./pages/Dashboard";
import EvalReport from "./pages/EvalReport";
import Policy from "./pages/Policy";
import Queue from "./pages/Queue";
import TicketView from "./pages/TicketView";

type Page = { name: "queue" } | { name: "inbox" } | { name: "ticket"; id: string } | { name: "dashboard" } | { name: "eval" } | { name: "policy" } | { name: "embed" };

export default function App() {
  const [page, setPage] = useState<Page>({ name: "queue" });
  const [role, setRole] = useState<"admin" | "customer" | null>(null);
  const [mode, setMode] = useState("");
  useEffect(() => { api.health().then((h) => setMode(h.llm_mode)).catch(() => setMode("offline")); }, []);

  async function logout() { try { await api.logout(); } catch { /* session may already have expired */ } setSessionToken(""); setRole(null); setPage({name:"queue"}); }
  const embedded = new URLSearchParams(window.location.search).get("embed") === "1";
  if (!role) return <Login onLogin={setRole} />;
  if (role === "customer") return <CustomerChat logout={logout} />;
  if (embedded) return <div className="p-5 text-sm">У віджеті увійдіть як клієнт. <button onClick={logout} className="underline">Вийти</button></div>;
  const tab = (name: Page["name"], label: string) => (
    <button onClick={() => setPage({ name } as Page)}
      className={`px-3 py-1.5 rounded-md text-sm ${page.name === name ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-200"}`}>
      {label}
    </button>
  );

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 bg-white border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 h-14 flex items-center gap-4">
          <span className="font-bold text-lg">💣 Defuse</span>
          <nav className="flex gap-1">{tab("queue", "Queue")}{tab("inbox", "Inbox")}{tab("dashboard", "Dashboard")}{tab("eval", "Eval")}{tab("policy", "Policy")}{tab("embed", "Вбудувати чат")}</nav>
          <button onClick={logout} className="ml-auto text-xs text-slate-500">Вийти</button><span className="text-xs text-slate-500">LLM mode: <b>{mode}</b></span>
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
