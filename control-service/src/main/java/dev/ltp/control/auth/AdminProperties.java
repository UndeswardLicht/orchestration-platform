package dev.ltp.control.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first admin account, created on start when no admin exists (US-23, NFR-09). Bound from
 * {@code LTP_ADMIN_LOGIN} and {@code LTP_ADMIN_PASSWORD}; the password is only needed until then.
 */
@ConfigurationProperties("ltp.admin")
public record AdminProperties(String login, String password) {}
