import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { RootCause } from "../types";

export default function RootCauseChart({ rows }: { rows: RootCause[] }) {
  const data = rows.map((r) => ({ name: r.root_cause.replaceAll("_", " "), share: Math.round(r.share * 100) }));
  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
        <XAxis type="number" unit="%" />
        <YAxis type="category" dataKey="name" width={130} tick={{ fontSize: 12 }} />
        <Tooltip formatter={(v) => `${v}%`} />
        <Bar dataKey="share" fill="#0f172a" radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
