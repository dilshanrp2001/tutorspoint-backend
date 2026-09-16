package com.tutorspoint.admin.repository;

import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Accounts as moderation sees them: every account, searchable and filterable.
 *
 * <p>A second repository over {@link User} rather than more methods on the auth one. Auth looks
 * accounts up one at a time by an identity it was handed; this one lists them all, and keeping
 * the two apart means the auth repository cannot quietly grow a query that returns everybody.
 */
public interface AdminUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** A count per role for one role's registrations in a window. */
    interface RoleCount {
        Role getRole();

        long getTotal();
    }

    /** Accounts created in {@code [from, to)}, per role. A role with none is simply absent. */
    @Query("""
            select u.role as role, count(u) as total
            from User u
            where u.createdAt >= :from and u.createdAt < :to
            group by u.role
            """)
    List<RoleCount> countRegistrationsByRole(@Param("from") Instant from, @Param("to") Instant to);
}
