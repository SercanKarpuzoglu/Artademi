import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatMoney } from '../../lib/format';

// design-reference paleti (CSS değişkenlerinin hex karşılıkları; uydurma renk yok).
const GREEN = '#2f7d5b';
const RED = '#b23a3a';
const RASP = '#b43a5e';
const LINE = '#e7e3ec';

interface TrendPoint {
  donem: string;
  tahsilat: number;
  gider?: number;
  net?: number;
}

/**
 * 6 aylık finans trendi. ADMIN: tahsilat+gider+net (3 seri); ACCOUNTING: yalnız tahsilat
 * ({@code showGiderNet=false}).
 */
export default function TrendChart({
  data,
  showGiderNet,
}: {
  data: TrendPoint[];
  showGiderNet: boolean;
}) {
  return (
    <div className="h-[260px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={LINE} />
          <XAxis dataKey="donem" tick={{ fontSize: 12 }} stroke="#5d5366" />
          <YAxis tick={{ fontSize: 12 }} width={70} stroke="#5d5366" tickFormatter={(v) => formatMoney(v)} />
          <Tooltip formatter={(v: unknown) => `${formatMoney(Number(v))} ₺`} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="tahsilat" name="Tahsilat" stroke={GREEN} strokeWidth={2} dot={false} />
          {showGiderNet && (
            <Line type="monotone" dataKey="gider" name="Gider" stroke={RED} strokeWidth={2} dot={false} />
          )}
          {showGiderNet && (
            <Line type="monotone" dataKey="net" name="Net" stroke={RASP} strokeWidth={2} dot={false} />
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
