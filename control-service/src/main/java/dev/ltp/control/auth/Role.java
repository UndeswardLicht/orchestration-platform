package dev.ltp.control.auth;

/** User roles (US-23). Stored as text; the database allows only these three values. */
public enum Role {
    ADMIN,
    DEVELOPER,
    VIEWER
}
