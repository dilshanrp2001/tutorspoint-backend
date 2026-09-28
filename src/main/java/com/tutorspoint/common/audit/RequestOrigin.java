package com.tutorspoint.common.audit;

import java.util.Optional;

/**
 * Where the current request came from, for the audit record.
 *
 * <p>An interface for the same reason as {@code CurrentUser}: the implementation reads a
 * servlet thread-local, and nothing that records an audit entry should know that requests
 * arrive over HTTP.
 */
public interface RequestOrigin {

    /** The client's address, or empty when there is no request (a startup task, a test). */
    Optional<String> clientAddress();
}
