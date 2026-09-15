-- V7__tutor_search.sql
--
-- What tutor search (FR-S1 - FR-S7) needs from the schema: two rating columns to filter and
-- sort on, a full-text document for keyword search, a distance function, and the indexes
-- that keep the whole thing inside the NFR-1 two-second budget.
--
-- Four decisions shape this file.
--
-- 1. The rating is denormalised onto the profile. Reviews arrive in Phase 7; when they do,
--    each one updates these two columns through TutorProfile.summariseReviews(). Search
--    reads the rating on every request, and an aggregate over a reviews table on every
--    request is the cost the NFR budget cannot pay.
--
-- 2. The keyword document is a column on tutor_profiles kept current by triggers, not a
--    generated column. It covers subject names, which live in two other tables, and a
--    generated column may only read its own row. The triggers are index maintenance, not
--    business rules: they decide nothing, they only keep a derived value in step.
--
-- 3. The 'simple' text-search configuration, not 'english'. Profiles and searches are in
--    Sinhala, Tamil and English, and PostgreSQL has no stemmer for the first two; an
--    English stemmer applied to a Sinhala word is at best a no-op. 'simple' lower-cases and
--    splits, and prefix matching (see tutor_search_query) covers most of what stemming
--    would have bought: "chem" finds Chemistry, and "ගණිත" finds ගණිතය.
--
-- 4. Every subject name goes in, in all three languages. A parent typing in Tamil must find
--    a tutor who filled the wizard in English, and the translations are already in the
--    database for exactly that kind of purpose.

-- ---------------------------------------------------------------------------
-- Rating summary (filled from Phase 7)
-- ---------------------------------------------------------------------------

ALTER TABLE tutor_profiles
    ADD COLUMN average_rating NUMERIC(3,2),
    ADD COLUMN review_count   INTEGER NOT NULL DEFAULT 0,
    -- No reviews and no average, or some reviews and an average on the 1-5 scale. An
    -- average of nothing is not zero stars, and sorting it as zero would bury new tutors.
    ADD CONSTRAINT ck_tutor_profiles_rating CHECK (
        (review_count = 0 AND average_rating IS NULL)
        OR (review_count > 0 AND average_rating BETWEEN 1 AND 5));

-- ---------------------------------------------------------------------------
-- Keyword search document (FR-S7)
-- ---------------------------------------------------------------------------

ALTER TABLE tutor_profiles ADD COLUMN search_document TSVECTOR;

-- The document for one profile. Weighted so a match in the headline or a subject name ranks
-- above the same word buried in a bio: A for the headline, B for subjects, C for the bio.
CREATE FUNCTION tutor_profile_search_document(p_profile_id BIGINT, p_headline TEXT, p_bio TEXT)
    RETURNS TSVECTOR
    LANGUAGE sql
    STABLE
AS $$
    SELECT setweight(to_tsvector('simple', coalesce(p_headline, '')), 'A')
        || setweight(to_tsvector('simple', coalesce((
               SELECT string_agg(st.name, ' ')
               FROM tutor_profile_subjects tps
               JOIN subject_translations st ON st.subject_id = tps.subject_id
               WHERE tps.tutor_profile_id = p_profile_id), '')), 'B')
        || setweight(to_tsvector('simple', coalesce(p_bio, '')), 'C')
$$;

-- What a parent typed, as a prefix query: every word must match the start of some word in
-- the document. plainto_tsquery does the parsing, so nothing a user types can be read as
-- tsquery syntax; each quoted lexeme it produces is then marked as a prefix. NULL when the
-- input contained no words at all, which tutor_search_matches treats as no filter.
CREATE FUNCTION tutor_search_query(keyword TEXT)
    RETURNS TSQUERY
    LANGUAGE sql
    IMMUTABLE
AS $$
    SELECT CASE
               WHEN plain IS NULL OR numnode(plain) = 0 THEN NULL
               ELSE regexp_replace(plain::text, '''((?:[^'']|'''')+)''', '''\1'':*', 'g')::tsquery
           END
    FROM plainto_tsquery('simple', keyword) AS plain
$$;

-- The two functions search calls. Written as single expressions so the planner inlines them,
-- which is what lets it see "search_document @@ <constant>" and use the GIN index below.
CREATE FUNCTION tutor_search_matches(document TSVECTOR, keyword TEXT)
    RETURNS BOOLEAN
    LANGUAGE sql
    IMMUTABLE
AS $$
    SELECT tutor_search_query(keyword) IS NULL OR document @@ tutor_search_query(keyword)
$$;

CREATE FUNCTION tutor_search_rank(document TSVECTOR, keyword TEXT)
    RETURNS REAL
    LANGUAGE sql
    IMMUTABLE
AS $$
    SELECT coalesce(ts_rank(document, tutor_search_query(keyword)), 0)
$$;

-- Keeping the document current. Three things feed it, so three triggers:

-- (a) The profile's own text. BEFORE, so the document is written in the same row version
--     rather than by a second UPDATE.
CREATE FUNCTION tutor_profiles_refresh_search_document()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_document := tutor_profile_search_document(NEW.id, NEW.headline, NEW.bio);
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_tutor_profiles_search_document
    BEFORE INSERT OR UPDATE OF headline, bio ON tutor_profiles
    FOR EACH ROW
EXECUTE FUNCTION tutor_profiles_refresh_search_document();

-- (b) The subjects a profile teaches. Saving a draft replaces the whole set, so this fires
--     on the deletes and the inserts alike.
CREATE FUNCTION tutor_profile_subjects_refresh_search_document()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
    v_profile_id BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.tutor_profile_id ELSE NEW.tutor_profile_id END;
BEGIN
    UPDATE tutor_profiles tp
    SET search_document = tutor_profile_search_document(tp.id, tp.headline, tp.bio)
    WHERE tp.id = v_profile_id;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_tutor_profile_subjects_search_document
    AFTER INSERT OR DELETE ON tutor_profile_subjects
    FOR EACH ROW
EXECUTE FUNCTION tutor_profile_subjects_refresh_search_document();

-- (c) A subject's names. Rare - a corrected translation in a reference-data migration - but
--     without it a fixed Tamil spelling would stay unsearchable on every existing profile.
CREATE FUNCTION subject_translations_refresh_search_document()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
    v_subject_id BIGINT := CASE WHEN TG_OP = 'DELETE' THEN OLD.subject_id ELSE NEW.subject_id END;
BEGIN
    UPDATE tutor_profiles tp
    SET search_document = tutor_profile_search_document(tp.id, tp.headline, tp.bio)
    WHERE tp.id IN (SELECT tps.tutor_profile_id FROM tutor_profile_subjects tps WHERE tps.subject_id = v_subject_id);
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_subject_translations_search_document
    AFTER INSERT OR UPDATE OR DELETE ON subject_translations
    FOR EACH ROW
EXECUTE FUNCTION subject_translations_refresh_search_document();

-- Profiles that existed before this migration.
UPDATE tutor_profiles
SET search_document = tutor_profile_search_document(id, headline, bio);

-- ---------------------------------------------------------------------------
-- Distance (FR-S2)
-- ---------------------------------------------------------------------------

-- Great-circle distance in kilometres by the Haversine formula, on a spherical Earth of
-- radius 6371 km. Accurate to well under one per cent at the scale of a district, which is
-- far finer than an area centre point is. least(1, ...) guards asin against a rounding
-- error pushing its argument a hair above one for two nearly identical points.
CREATE FUNCTION great_circle_km(lat1 DOUBLE PRECISION, lon1 DOUBLE PRECISION,
                                lat2 DOUBLE PRECISION, lon2 DOUBLE PRECISION)
    RETURNS DOUBLE PRECISION
    LANGUAGE sql
    IMMUTABLE
    STRICT
AS $$
    SELECT 2 * 6371 * asin(least(1, sqrt(
               power(sin(radians(lat2 - lat1) / 2), 2)
               + cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lon2 - lon1) / 2), 2))))
$$;

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
--
-- Search only ever reads published profiles, so the sort indexes are partial on that: they
-- stay small, and a draft being edited never touches them. The subject, exam level,
-- syllabus and area joins already have their reverse indexes from V5.

CREATE INDEX idx_tutor_profiles_search_document ON tutor_profiles USING GIN (search_document);

-- The price band filter reads both ends of the range; price-ascending sorts on the lower one.
CREATE INDEX idx_tutor_profiles_published_fee
    ON tutor_profiles (fee_min, fee_max) WHERE status = 'PUBLISHED';

CREATE INDEX idx_tutor_profiles_published_rating
    ON tutor_profiles (average_rating DESC NULLS LAST, review_count DESC) WHERE status = 'PUBLISHED';

CREATE INDEX idx_tutor_profiles_published_experience
    ON tutor_profiles (years_of_experience DESC NULLS LAST) WHERE status = 'PUBLISHED';

-- The two yes/no filters, together, because they are usually asked together.
CREATE INDEX idx_tutor_profiles_published_flags
    ON tutor_profiles (verified, available_online) WHERE status = 'PUBLISHED';

-- "Profiles teaching in this medium / this format". V5 indexed these tables by profile only.
CREATE INDEX idx_tpm_medium ON tutor_profile_mediums (medium, tutor_profile_id);
CREATE INDEX idx_tpcf_class_format ON tutor_profile_class_formats (class_format, tutor_profile_id);
