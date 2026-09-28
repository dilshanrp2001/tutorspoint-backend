package com.tutorspoint.auth.config;

/**
 * How the phone verification code reaches the user (FR-A2).
 *
 * <p>{@link #SMS} is what the requirement asks for, and the only value that proves the
 * user holds the number they registered. {@link #EMAIL} is a deliberate interim
 * compromise while no SMS gateway account exists: the code travels to the address the
 * account was registered with, which costs nothing because the mail transport is already
 * paid for. It verifies the address rather than the handset, so the phone number stands
 * unproven until this is switched back.
 */
public enum OtpDelivery {

    /** Texted to the registered phone number, over the configured gateway. */
    SMS,

    /** Emailed to the registered address. No per-message cost, and no proof of the phone. */
    EMAIL
}
