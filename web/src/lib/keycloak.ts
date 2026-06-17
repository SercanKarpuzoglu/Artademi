import Keycloak from 'keycloak-js';

/**
 * Tek Keycloak instance. Yapilandirma .env (VITE_*) uzerinden gelir.
 * Token YALNIZCA bellekte tutulur (keycloak-js varsayilani); localStorage'a YAZILMAZ.
 */
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

/**
 * Uygulama acilirken cagrilir. login-required: kimlik dogrulanmadan iceri girilemez.
 * PKCE S256: public client guvenligi. checkLoginIframe kapali (yerel gelistirmede gurultu yapmasin).
 */
export function initKeycloak(): Promise<boolean> {
  return keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  });
}
