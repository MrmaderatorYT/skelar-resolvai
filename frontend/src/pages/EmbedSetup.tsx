import { useState } from "react";
import { Check, Code2, Copy, ExternalLink } from "lucide-react";

export default function EmbedSetup() {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const url = `${window.location.origin}/?embed=1`;
  const snippet = `<iframe\n  src="${url}"\n  title="Чат підтримки"\n  loading="lazy"\n  style="width:100%;max-width:480px;height:720px;border:0;border-radius:16px;"\n></iframe>`;
  const local = ["localhost", "127.0.0.1", "::1"].includes(window.location.hostname);
  async function copy() {
    try { await navigator.clipboard.writeText(snippet); setCopied(true); setCopyError(false); }
    catch { setCopyError(true); }
  }
  return <div className="mx-auto max-w-4xl space-y-6 py-6">
    <div><div className="mb-3 inline-flex rounded-xl bg-emerald-50 p-3 text-emerald-700"><Code2 size={22}/></div><h1 className="text-2xl font-bold tracking-tight text-slate-900">Embed the chat on a website</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">Add this code to the page HTML. The chat opens in its own iframe and uses the same ticket server.</p></div>
    <div className="rounded-xl border border-slate-200 bg-white p-5"><div className="mb-3 flex items-center justify-between gap-4"><h2 className="text-sm font-semibold">Embed code</h2><button onClick={copy} className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50">{copied ? <Check size={14}/> : <Copy size={14}/>} {copied ? "Copied" : "Copy"}</button></div><pre className="overflow-x-auto rounded-lg bg-slate-900 p-4 text-xs leading-6 text-slate-100"><code>{snippet}</code></pre>{copyError && <p className="mt-2 text-xs text-amber-700">Could not copy automatically. Select the code and copy it manually.</p>}</div>
    {local && <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">This is a local address that only works on this computer. For other sites, host the app on a public HTTPS domain and replace the address in the code.</div>}
    <div className="rounded-xl border border-slate-200 bg-white p-5 text-sm leading-6 text-slate-600"><h2 className="font-semibold text-slate-900">Current demo limits</h2><p className="mt-2">The widget needs a demo customer sign-in. Real identity verification and customer isolation are not implemented yet. Do not connect the demo to real customer data.</p><a href="/?embed=1" target="_blank" rel="noreferrer" className="mt-4 inline-flex items-center gap-1.5 font-semibold text-emerald-700">Open widget <ExternalLink size={14}/></a></div>
  </div>;
}
