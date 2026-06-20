/**
 * Uygulama rolleri tek kaynaktan. String typo'larini engellemek icin her yerde
 * {@link Role} kullanilir (ham string degil).
 *
 * Bu roller Keycloak token'indaki {@code realm_access.roles} icinden gelir; Keycloak'in
 * teknik rolleri ({@code default-roles-*}, {@code offline_access}, {@code uma_authorization})
 * {@link filterDomainRoles} ile elenir.
 */
export const Role = {
  ADMIN: 'ADMIN',
  FRONTDESK: 'FRONTDESK',
  FRONTDESK_ACCOUNTING: 'FRONTDESK_ACCOUNTING',
  TEACHER: 'TEACHER',
  SUPER_ADMIN: 'SUPER_ADMIN',
} as const;

export type Role = (typeof Role)[keyof typeof Role];

/** Tanidigimiz tum domain rolleri. */
export const DOMAIN_ROLES: readonly Role[] = Object.values(Role);

/** Insana okunur rol etiketleri (rozet/menude gosterim icin). */
export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Yönetici',
  FRONTDESK: 'Ön Büro',
  FRONTDESK_ACCOUNTING: 'Ön Büro / Muhasebe',
  TEACHER: 'Öğretmen',
  SUPER_ADMIN: 'Platform Yöneticisi',
};

/** Ham rol listesinden yalnizca domain rollerini suzer (Keycloak teknik rollerini atar). */
export function filterDomainRoles(raw: readonly string[]): Role[] {
  return raw.filter((r): r is Role => (DOMAIN_ROLES as readonly string[]).includes(r));
}

/**
 * Birden cok rol varsa "birincil" rolu oncelik sirasina gore secer
 * (ADMIN > FRONTDESK_ACCOUNTING > FRONTDESK > TEACHER > SUPER_ADMIN).
 * Rozet ve giris sonrasi yonlendirmede kullanilir.
 */
const ONCELIK: Role[] = [
  Role.ADMIN,
  Role.SUPER_ADMIN,
  Role.FRONTDESK_ACCOUNTING,
  Role.FRONTDESK,
  Role.TEACHER,
];

export function primaryRole(roles: readonly Role[]): Role | null {
  for (const r of ONCELIK) {
    if (roles.includes(r)) return r;
  }
  return roles[0] ?? null;
}
