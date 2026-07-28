package com.artademi.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link BillingProperties}'i etkinlestirir (user modulundeki KeycloakProperties deseni). */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingConfig {
}
