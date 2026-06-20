package com.artademi.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Kullanici yonetimi modulu yapilandirmasi. {@link KeycloakProperties}'i etkinlestirir ve
 * Keycloak Admin REST cagrilari icin tek bir {@link RestClient} bean'i saglar
 * (spring-web; webflux/keycloak-admin-client EKLENMEZ).
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class UserConfig {

    /** Keycloak'a giden HTTP cagrilari icin paylasilan RestClient. */
    @Bean
    RestClient keycloakRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
