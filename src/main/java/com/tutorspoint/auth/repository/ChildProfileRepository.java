package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Child sub-profiles (FR-A5).
 *
 * <p>Every finder is scoped by the owning parent. Loading a child by id alone would make
 * it the caller's job to remember the ownership check, and a forgotten check is an
 * authorization hole; the query carries it instead.
 */
public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {

    List<ChildProfile> findByParentIdOrderByNameAsc(Long parentId);

    Optional<ChildProfile> findByIdAndParentId(Long id, Long parentId);
}
