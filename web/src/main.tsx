import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import PublicBasvuruPage from './features/basvuru/PublicBasvuruPage';
import './index.css';
import { initKeycloak } from './lib/keycloak';
import { queryClient } from './lib/queryClient';

const rootEl = document.getElementById('root')!;

/**
 * Kimlik GEREKTİRMEYEN rotalar. Bu ön ekle gelen istekte Keycloak HİÇ başlatılmaz.
 *
 * ⚠️ Kritik: `initKeycloak()` `login-required` ile çalışır, yani çağrıldığı anda kullanıcıyı
 * giriş ekranına yönlendirir. Ön kayıt formunu dolduran veli için bu, formu hiç görmeden
 * giriş ekranına düşmek demektir. Bu yüzden dallanma render'dan ÖNCE, en dışta yapılır.
 */
const PUBLIC_BASVURU_ONEK = '/basvuru/';

function publicBasvuruSlug(): string | null {
  const yol = window.location.pathname;
  if (!yol.startsWith(PUBLIC_BASVURU_ONEK)) {
    return null;
  }
  const slug = yol.slice(PUBLIC_BASVURU_ONEK.length).split('/')[0];
  return slug ? decodeURIComponent(slug) : null;
}

const slug = publicBasvuruSlug();

if (slug) {
  // Public form: Keycloak yok, AuthProvider yok, AppShell yok — hepsi kimliğe bağlıdır.
  createRoot(rootEl).render(
    <StrictMode>
      <PublicBasvuruPage slug={slug} />
    </StrictMode>,
  );
} else {
  // Panel: önce Keycloak ile kimlik doğrula, sonra uygulamayı render et.
  initKeycloak()
    .then(() => {
      createRoot(rootEl).render(
        <StrictMode>
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <BrowserRouter>
                <App />
              </BrowserRouter>
            </AuthProvider>
          </QueryClientProvider>
        </StrictMode>,
      );
    })
    .catch((err) => {
      console.error('Keycloak baslatilamadi', err);
      rootEl.innerHTML =
        '<p style="font-family:sans-serif;padding:2rem;color:#b91c1c">' +
        'Kimlik doğrulama başlatılamadı. Keycloak çalışıyor mu? (http://localhost:8080)' +
        '</p>';
    });
}
