import { useId } from "react";

export default function ReplyEditor({ value, onChange, editable }: { value: string; onChange: (v: string) => void; editable: boolean }) {
  const id = useId();
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <label htmlFor={id} className="block text-sm font-semibold mb-2">Reply to customer {editable ? "(draft — edit before Approve or Take over)" : "(sent)"}</label>
      <textarea id={id} value={value} onChange={(e) => onChange(e.target.value)} readOnly={!editable} rows={6} maxLength={2000}
        className="w-full rounded-md border border-slate-200 p-2 text-sm bg-slate-50 focus:bg-white" />
    </section>
  );
}
