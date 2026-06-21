package com.artademi.common.exception;

/**
 * Tenant'in (kurumun) erisimi askiya alinmis ({@code status=ASKIDA}). Token gecerli olsa bile
 * is verisine erisilemez. GlobalExceptionHandler bunu 403 / {@code TENANT_SUSPENDED}'a cevirir.
 * (Abonelik "odeme gelmezse erisimi kes" mekanizmasinin guvenlik siniri.)
 */
public class TenantSuspendedException extends RuntimeException {

    public TenantSuspendedException(String message) {
        super(message);
    }
}
