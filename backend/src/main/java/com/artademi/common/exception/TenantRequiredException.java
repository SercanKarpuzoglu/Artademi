package com.artademi.common.exception;

/**
 * Tenant gerektiren bir is ucuna tenant baglami olmadan erisildi.
 * GlobalExceptionHandler bunu 400 / TENANT_REQUIRED'a cevirir; sorgu hic calismaz.
 */
public class TenantRequiredException extends RuntimeException {

    public TenantRequiredException(String message) {
        super(message);
    }
}
