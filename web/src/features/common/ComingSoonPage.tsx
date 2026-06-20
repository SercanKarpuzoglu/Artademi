import { Construction } from 'lucide-react';

/**
 * "Yakında" placeholder — referans .card dili içinde ortalanmış ikon + kısa metin.
 * Sonraki job'larda gerçek modül ekranlarıyla değiştirilecek.
 */
export default function ComingSoonPage({ title }: { title: string }) {
  return (
    <>
      <div className="topbar">
        <div>
          <h1>{title}</h1>
          <div className="sub">Bu modül yakında eklenecek.</div>
        </div>
      </div>

      <div className="card flex flex-col items-center py-14 text-center">
        <span className="mb-4 grid h-12 w-12 place-items-center rounded-card bg-rasp-soft text-rasp">
          <Construction size={24} strokeWidth={1.75} />
        </span>
        <h3>{title}</h3>
        <p className="text-ink-soft">Bu modül için ekranlar hazırlanıyor.</p>
      </div>
    </>
  );
}
