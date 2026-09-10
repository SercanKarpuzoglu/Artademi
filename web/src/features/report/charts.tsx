import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

// design-reference paleti (TrendChart ile aynı); grafik metinleri tokenlarla değil hex ile — recharts SVG.
export const RENK = {
  green: '#2f7d5b',
  red: '#b23a3a',
  rasp: '#b43a5e',
  amber: '#b9791e',
  blue: '#2f5f9e',
  gray: '#8b8194',
  line: '#e7e3ec',
  ink: '#5d5366',
};

export interface Dilim {
  name: string;
  value: number;
  renk: string;
}

/** Halka grafik: dağılım (gelir kalemleri, katılım). Sıfır toplamda boş durum yazar. */
export function Halka({ data, format, yukseklik = 220 }: { data: Dilim[]; format?: (v: number) => string; yukseklik?: number }) {
  const dolu = data.filter((d) => d.value > 0);
  if (dolu.length === 0) {
    return <div className="grid h-[120px] place-items-center text-[13px] text-ink-soft">Veri yok</div>;
  }
  return (
    <div style={{ height: yukseklik }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={dolu} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="85%" paddingAngle={2} stroke="none">
            {dolu.map((d) => (
              <Cell key={d.name} fill={d.renk} />
            ))}
          </Pie>
          <Tooltip formatter={(v: unknown) => (format ? format(Number(v)) : String(v))} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export interface CubukSeri {
  key: string;
  name: string;
  renk: string;
}

/** Yatay çubuk: kategori adı solda, değer sağda (borç, doluluk, hakediş). İsteğe bağlı yığın (stack). */
export function YatayCubuk({
  data,
  seriler,
  nameKey = 'name',
  format,
  yigin,
  yukseklik,
}: {
  data: Record<string, unknown>[];
  seriler: CubukSeri[];
  nameKey?: string;
  format?: (v: number) => string;
  yigin?: boolean;
  yukseklik?: number;
}) {
  if (data.length === 0) {
    return <div className="grid h-[120px] place-items-center text-[13px] text-ink-soft">Veri yok</div>;
  }
  const h = yukseklik ?? Math.max(160, 28 * data.length + 40);
  return (
    <div style={{ height: h }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={RENK.line} horizontal={false} />
          <XAxis type="number" tick={{ fontSize: 11 }} stroke={RENK.ink} tickFormatter={(v) => (format ? format(Number(v)) : String(v))} />
          <YAxis type="category" dataKey={nameKey} width={140} tick={{ fontSize: 12 }} stroke={RENK.ink} />
          <Tooltip formatter={(v: unknown) => (format ? format(Number(v)) : String(v))} />
          {seriler.length > 1 && <Legend wrapperStyle={{ fontSize: 12 }} />}
          {seriler.map((s) => (
            <Bar key={s.key} dataKey={s.key} name={s.name} fill={s.renk} stackId={yigin ? 'y' : undefined} radius={yigin ? 0 : [0, 6, 6, 0]} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
