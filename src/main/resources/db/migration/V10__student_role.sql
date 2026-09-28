-- V10__student_role.sql
--
-- Student becomes an account role of its own (PRD v1.1 FR-A1, FR-A8). Until now a student
-- registered as a Parent, which made "a learner acting for themselves" and "an adult acting
-- for a child" indistinguishable, and handed every student a child sub-profile feature that
-- does not describe them.
--
-- Parent and Student are the demand side, and everything the demand side owns - enquiries,
-- shortlists, and later reviews and requests - belongs to either. So the two share a new
-- JOINED level between users and themselves: `seekers` (architecture section 9, ADR
-- "Demand-side roles"). Enquiries and shortlists point at `seekers`, which keeps the foreign
-- key a guarantee the database enforces (a tutor still cannot own a shortlist) without a
-- second copy of each table per role. Child profiles stay on `parents`: children are a
-- Parent concern, and a Student has none.
--
-- Existing accounts are not reclassified. Every current parent stays a parent, which is also
-- why the back-fill below is a plain copy of the parents table.

ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('TUTOR', 'PARENT', 'STUDENT', 'ADMIN'));

-- ---------------------------------------------------------------------------
-- The demand-side level
-- ---------------------------------------------------------------------------

CREATE TABLE seekers (
    id BIGINT PRIMARY KEY,
    CONSTRAINT fk_seekers_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);

INSERT INTO seekers (id) SELECT id FROM parents;

-- A parent is now a seeker first. The cascade chain users -> seekers -> parents is the same
-- one users -> parents was, one level longer.
ALTER TABLE parents DROP CONSTRAINT fk_parents_user;
ALTER TABLE parents ADD CONSTRAINT fk_parents_seeker FOREIGN KEY (id) REFERENCES seekers (id) ON DELETE CASCADE;

-- Empty of columns of its own, like parents was when it was created: the table is what
-- makes "this account is a student" a fact in the database.
CREATE TABLE students (
    id BIGINT PRIMARY KEY,
    CONSTRAINT fk_students_seeker FOREIGN KEY (id) REFERENCES seekers (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Enquiries and shortlists belong to a seeker
-- ---------------------------------------------------------------------------
--
-- Renaming a column carries its indexes, the partial unique index and the unique constraint
-- with it; only the names that still say "parent" are renamed alongside. The foreign keys
-- are replaced rather than renamed because their target changes. Every existing parent_id
-- is already a row in seekers (the back-fill above), so the new constraints validate.

ALTER TABLE enquiries RENAME COLUMN parent_id TO seeker_id;
ALTER TABLE enquiries DROP CONSTRAINT fk_enquiries_parent;
ALTER TABLE enquiries ADD CONSTRAINT fk_enquiries_seeker FOREIGN KEY (seeker_id) REFERENCES seekers (id);
ALTER INDEX idx_enquiries_parent RENAME TO idx_enquiries_seeker;

ALTER TABLE shortlists RENAME COLUMN parent_id TO seeker_id;
ALTER TABLE shortlists DROP CONSTRAINT fk_shortlists_parent;
ALTER TABLE shortlists ADD CONSTRAINT fk_shortlists_seeker FOREIGN KEY (seeker_id) REFERENCES seekers (id) ON DELETE CASCADE;
ALTER INDEX idx_shortlists_parent RENAME TO idx_shortlists_seeker;
