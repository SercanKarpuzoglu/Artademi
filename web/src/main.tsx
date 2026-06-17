import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';
import { initKeycloak } from './lib/keycloak';
import { queryClient } from './lib/queryClient';

const rootEl = document.getElementById('root')!;

// Once Keycloak ile kimlik dogrula (login-required), sonra uygulamayi render et.
initKeycloak()
  .then(() => {
    createRoot(rootEl).render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
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
