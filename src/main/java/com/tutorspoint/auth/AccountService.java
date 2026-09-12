package com.tutorspoint.auth;

import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.UpdateAccountRequest;

/**
 * What a signed-in user may do to their own account (FR-A7).
 *
 * <p>Every call here is implicitly scoped to the caller — there is no account id in any
 * signature, which is the strongest form the ownership rule can take: a parameter that does
 * not exist cannot be set to somebody else's id.
 */
public interface AccountService {

    /** The caller's own details. */
    AccountResponse myAccount();

    /** Edits the caller's own details. */
    AccountResponse updateMyAccount(UpdateAccountRequest request);

    /**
     * Soft-deletes the caller's account and ends every session (FR-A7).
     *
     * <p>The row survives for referential integrity and audit: enquiries, reviews and
     * payments made by this account must not lose their author. A consequence worth knowing
     * is that the email address and phone number stay taken — reusing them needs an
     * administrator, not a second registration.
     */
    void deleteMyAccount();
}
