package dev.ltp.control.auth;

/** Published when a user's role changes; that user's open sessions end after the commit. */
record UserAccessChanged(long userId) {}
