package dev.ltp.control.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security (NFR-05). Only the allow-list below is public.
 *
 * <p>Skeleton only: US-23 replaces this with session login + CSRF cookie, US-24 adds the API-token
 * filter, and US-06 adds a separate chain for {@code /internal/**} (worker secret).
 */
@Configuration
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    /**
     * Salted password hashes with an algorithm prefix, bcrypt by default (NFR-05). The prefix lets a
     * later switch to argon2 keep old hashes valid; the database accepts only these prefixes.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
