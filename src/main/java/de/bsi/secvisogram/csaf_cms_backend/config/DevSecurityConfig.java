package de.bsi.secvisogram.csaf_cms_backend.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Provides a dummy {@link JwtDecoder} bean so the application can start without a running
 * Keycloak instance. Active only when the {@code dev} Spring profile is enabled.
 *
 * <p>Usage: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev}
 *
 * <p>All endpoints remain secured; you can call them with a self-signed JWT using the
 * symmetric key defined below (HS256, secret: {@code devsecretkey-do-not-use-in-prod!!}).
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DevSecurityConfig.class);

    private static final String DEV_SECRET = "devsecretkey-do-not-use-in-prod!!";

    @Bean
    JwtDecoder jwtDecoder() {
        LOG.warn("Dev profile active — using a dummy JwtDecoder. "
                + "Do NOT use this in production!");
        SecretKeySpec key = new SecretKeySpec(DEV_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
