import { useAuth } from '../../auth/AuthContext';

/**
 * Karşılama ekranı — referans .topbar + .card dili. Modüller eklendikçe burası özet
 * .stat kartları (öğrenci sayısı, gelir, bekleyen ödeme...) ile dolacak.
 */
export default function DashboardPage() {
  const { username } = useAuth();

  return (
    <>
      <div className="topbar">
        <div>
          <h1>Hoş geldin, {username}</h1>
          <div className="sub">Modüller eklendikçe panel burada özetlenecek.</div>
        </div>
      </div>

      <div className="card">
        <h3>Genel Bakış</h3>
        <p className="text-ink-soft">Modüller yakında eklenecek.</p>
      </div>

      {/* TODO: Özet .stat kartları (Aktif Öğrenci, Haziran Geliri, Bekleyen Ödeme,
          Net) modüller hazır oldukça design-reference.html'deki .grid.stats düzeniyle eklenecek. */}
    </>
  );
}
