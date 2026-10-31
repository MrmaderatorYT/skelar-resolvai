export default function ReplyEditor({ value, onChange, editable }: { value: string; onChange: (v: string) => void; editable: boolean }) {
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h3 className="text-sm font-semibold mb-2">Reply to customer {editable ? "(draft)" : "(sent)"}</h3>
      <textarea value={value} onChange={(e) => onChange(e.target.value)} readOnly={!editable} rows={6}
        className="w-full rounded-md border border-slate-200 p-2 text-sm bg-slate-50 focus:bg-white" />
    </section>
  );
}
