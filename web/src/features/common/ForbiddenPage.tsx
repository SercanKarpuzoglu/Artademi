import { ShieldX } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

/** 403 — referans .card dili. Asıl yetki backend'de; bu yalnızca UX. */
export default function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <>
      <div className="topbar">
        <div>
          <h1>Erişim yok</h1>
          <div className="sub">Bu sayfaya erişim yetkiniz yok.</div>
        </div>
      </div>

      <div className="card flex flex-col items-center py-14 text-center">
        <span className="mb-4 grid h-12 w-12 place-items-center rounded-card bg-red-soft text-red">
          <ShieldX size={24} strokeWidth={1.75} />
        </span>
        <h3>Bu sayfaya erişim yetkiniz yok</h3>
        <p className="mb-4 text-ink-soft">Yetkili bir hesapla giriş yaptığınızdan emin olun.</p>
        <button type="button" className="btn btn-primary" onClick={() => navigate('/')}>
          Ana sayfaya dön
        </button>
      </div>
    </>
  );
}
