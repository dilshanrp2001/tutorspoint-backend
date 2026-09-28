package com.tutorspoint.admin.bootstrap;

/**
 * Creates administrator accounts. There is no HTTP route to this and there never will be:
 * administrators are provisioned by whoever controls the deployment's environment, not by anybody
 * who can reach the API.
 */
public interface AdminProvisioningService {

    enum Outcome {
        /** A new, active administrator account exists. */
        CREATED,
        /** An account with this email is already an administrator; nothing was changed. */
        ALREADY_ADMIN,
        /** The email or phone number belongs to a non-admin account; nothing was changed. */
        CONFLICT
    }

    /**
     * Creates an active administrator, verified on both channels, unless the email or phone
     * number is already taken - in which case nothing is changed, and in particular an existing
     * account's password is never overwritten from the environment.
     *
     * @throws IllegalArgumentException if a value is missing or the password is too short
     */
    Outcome provision(String email, String password, String fullName, String phoneNumber);
}
