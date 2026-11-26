package dev.ltp.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cookie settings (US-23). {@code secureCookies} marks the CSRF cookie Secure, like the session cookie
 * ({@code server.servlet.session.cookie.secure}); both come from {@code LTP_SECURE_COOKIES}.
 */
@ConfigurationProperties("ltp.security")
public record LtpSecurityProperties(@DefaultValue("true") boolean secureCookies) {}
