package com.tutorspoint.tutor.domain;

import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a tutor advertises: what they teach, how, where, and for how much (FR-T1 - FR-T8).
 *
 * <p>Separate from {@link Tutor} because the two have different lifecycles. The account
 * exists from the moment somebody registers and never goes away; the profile is drafted over
 * several sittings, published, taken down and published again, and is the only one of the two
 * the public ever sees. Folding them together would put a draft headline in the same row as
 * the password hash.
 *
 * <p><strong>The publish rule lives here, not in a service.</strong> Complete enough to show a
 * parent is a statement about the profile itself, and a rule that only holds when a caller
 * remembers to ask a service first is not really a rule. {@link #missingFields()} is the same
 * rule read forwards, so a wizard progress indicator and the publish check cannot disagree
 * about what is required.
 *
 * <p>No setters. Each mutator below covers one step of the profile wizard and is free to
 * refuse: {@link #chargesBetween} will not accept an inverted range, and nothing at all may be
 * edited once an administrator has suspended the profile.
 */
@Entity
@Table(name = "tutor_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TutorProfile extends BaseEntity {

    /** Stable keys the client switches on and translates into si / ta / en. */
    private static final String ERROR_PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
    private static final String ERROR_PROFILE_NOT_PUBLISHED = "PROFILE_NOT_PUBLISHED";
    private static final String ERROR_PROFILE_SUSPENDED = "PROFILE_SUSPENDED";

    /**
     * The owner. Not optional and not changeable: a profile is created for one tutor and
     * belongs to that tutor for as long as it exists, which is what makes every owner-scoped
     * query a single equality test.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false, unique = true, updatable = false)
    private Tutor tutor;

    /** The one line under the name in a search result: A/L Chemistry, 12 years, Nugegoda. */
    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "bio", length = 5000)
    private String bio;

    /** Set by the upload endpoint in Phase 2.3, which stores through {@code FileStorage}. */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "intro_video_url", length = 500)
    private String introVideoUrl;

    /**
     * Reference rows, never free text (architecture section 9): a search filter and a profile
     * have to be saying the same word for the filter to match. {@code @OrderBy} makes the
     * loaded set a {@code LinkedHashSet} in display order, so a response renders the same way
     * twice.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tutor_profile_subjects",
            joinColumns = @JoinColumn(name = "tutor_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id"))
    @OrderBy("displayOrder ASC")
    private Set<Subject> subjects = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tutor_profile_exam_levels",
            joinColumns = @JoinColumn(name = "tutor_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "exam_level_id"))
    @OrderBy("displayOrder ASC")
    private Set<ExamLevel> examLevels = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tutor_profile_syllabuses",
            joinColumns = @JoinColumn(name = "tutor_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "syllabus_id"))
    @OrderBy("displayOrder ASC")
    private Set<Syllabus> syllabuses = new LinkedHashSet<>();

    /** Where the tutor will travel to teach. Empty is legitimate for an online-only tutor. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tutor_profile_areas_served",
            joinColumns = @JoinColumn(name = "tutor_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "area_id"))
    @OrderBy("displayOrder ASC")
    private Set<Area> areasServed = new LinkedHashSet<>();

    /**
     * Closed sets with no reference table, so they are stored as their own names in a
     * collection table rather than joined to one. See {@link Medium} on why.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_mediums", joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "medium", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Medium> mediums = EnumSet.noneOf(Medium.class);

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_class_formats", joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "class_format", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<ClassFormat> classFormats = EnumSet.noneOf(ClassFormat.class);

    /** A list, not a set: the order the tutor arranged them in is the order they read. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tutor_profile_qualifications", joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @OrderColumn(name = "display_order")
    private List<Qualification> qualifications = new ArrayList<>();

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    /** Rupees. {@code NUMERIC(10,2)}, never a double - money that rounds is money that is wrong. */
    @Column(name = "fee_min", precision = 10, scale = 2)
    private BigDecimal feeMin;

    @Column(name = "fee_max", precision = 10, scale = 2)
    private BigDecimal feeMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_unit", length = 20)
    private FeeUnit feeUnit;

    /** Where the tutor teaches from, and the anchor for distance sorting in Phase 3. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_base_area_id")
    private Area homeBaseArea;

    @Column(name = "travel_radius_km")
    private Integer travelRadiusKm;

    @Column(name = "available_online", nullable = false)
    private boolean availableOnline;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProfileStatus status;

    /**
     * The verified badge (FR-R3). Set by an administrator after the document review in
     * {@code verification} (Phase 6) - never by the tutor, and never as a side effect of
     * publishing, which is why no mutator here touches it except {@link #verify}.
     */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /**
     * An empty draft for a tutor who has just reached the wizard. Everything optional starts
     * null, so {@link #missingFields()} lists all eight requirements - exactly what a progress
     * indicator wants on step one.
     */
    public TutorProfile(Tutor tutor) {
        this.tutor = requireNotNull(tutor, "tutor");
        this.status = ProfileStatus.DRAFT;
        this.availabilityStatus = AvailabilityStatus.ACCEPTING;
        this.availableOnline = false;
        this.verified = false;
    }

    // ---------------------------------------------------------------------
    // Editing - one method per step of the profile wizard
    // ---------------------------------------------------------------------

    /** Step 1: who this tutor is, in words (FR-T1). */
    public void describe(String headline, String bio) {
        ensureEditable();
        this.headline = trimToNull(headline);
        this.bio = trimToNull(bio);
    }

    /**
     * Step 1, the other half: the photograph, once it has been stored (FR-T1).
     *
     * <p>Separate from {@link #describe} and not part of any draft save, because the URL is not
     * the tutor's to choose. It is issued by the upload endpoint for a file the platform
     * accepted, re-encoded and stripped of its metadata; letting a draft carry an arbitrary
     * string here would let a profile point its photograph at anything on the internet.
     */
    public void attachPhoto(String photoUrl) {
        ensureEditable();
        this.photoUrl = requireText(photoUrl, "photoUrl").trim();
    }

    /** Removes the photograph. The profile stops being publishable until another is uploaded. */
    public void removePhoto() {
        ensureEditable();
        this.photoUrl = null;
    }

    /** As {@link #attachPhoto}, for the introduction video. Never required to publish. */
    public void attachIntroVideo(String introVideoUrl) {
        ensureEditable();
        this.introVideoUrl = requireText(introVideoUrl, "introVideoUrl").trim();
    }

    public void removeIntroVideo() {
        ensureEditable();
        this.introVideoUrl = null;
    }

    /** Step 2: what they teach (FR-T2). */
    public void teaches(Collection<Subject> subjects,
                        Collection<ExamLevel> examLevels,
                        Collection<Syllabus> syllabuses,
                        Collection<Medium> mediums) {
        ensureEditable();
        replace(this.subjects, subjects);
        replace(this.examLevels, examLevels);
        replace(this.syllabuses, syllabuses);
        replace(this.mediums, mediums);
    }

    /** Step 3: how they teach (FR-T4, FR-T6). */
    public void delivers(Collection<ClassFormat> classFormats,
                         boolean availableOnline,
                         AvailabilityStatus availabilityStatus) {
        ensureEditable();
        replace(this.classFormats, classFormats);
        this.availableOnline = availableOnline;
        this.availabilityStatus = requireNotNull(availabilityStatus, "availabilityStatus");
    }

    /**
     * Step 4: where they teach (FR-T5).
     *
     * @throws IllegalArgumentException if the travel radius is negative - a distance the
     *                                  domain has no meaning for
     */
    public void servesAreas(Area homeBaseArea, Collection<Area> areasServed, Integer travelRadiusKm) {
        ensureEditable();
        if (travelRadiusKm != null && travelRadiusKm < 0) {
            throw new IllegalArgumentException("travelRadiusKm must not be negative, was " + travelRadiusKm);
        }
        this.homeBaseArea = homeBaseArea;
        replace(this.areasServed, areasServed);
        this.travelRadiusKm = travelRadiusKm;
    }

    /** Step 5a: credentials, in the order the tutor wants them read (FR-T3). */
    public void listQualifications(List<Qualification> qualifications) {
        ensureEditable();
        this.qualifications.clear();
        if (qualifications != null) {
            // Distinct because Qualification has value equality: a resubmitted wizard step
            // must not turn one degree into two.
            qualifications.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(this.qualifications::add);
        }
    }

    /**
     * Step 5b: years taught (FR-T3).
     *
     * @throws IllegalArgumentException if negative
     */
    public void recordExperience(Integer yearsOfExperience) {
        ensureEditable();
        if (yearsOfExperience != null && yearsOfExperience < 0) {
            throw new IllegalArgumentException("yearsOfExperience must not be negative, was " + yearsOfExperience);
        }
        this.yearsOfExperience = yearsOfExperience;
    }

    /**
     * Step 5c: the fee range (FR-T3). All three values move together - a number without its
     * unit is not comparable, and a range with one end is not a range.
     *
     * @throws IllegalArgumentException if only part of the range is given, a fee is negative,
     *                                  or the minimum exceeds the maximum
     */
    public void chargesBetween(BigDecimal feeMin, BigDecimal feeMax, FeeUnit feeUnit) {
        ensureEditable();
        boolean allAbsent = feeMin == null && feeMax == null && feeUnit == null;
        boolean allPresent = feeMin != null && feeMax != null && feeUnit != null;
        if (!allAbsent && !allPresent) {
            throw new IllegalArgumentException(
                    "A fee range needs a minimum, a maximum and a unit, or none of the three");
        }
        if (allPresent) {
            requireNotNegative(feeMin, "feeMin");
            requireNotNegative(feeMax, "feeMax");
            if (feeMin.compareTo(feeMax) > 0) {
                throw new IllegalArgumentException(
                        "feeMin %s must not exceed feeMax %s".formatted(feeMin, feeMax));
            }
        }
        this.feeMin = feeMin;
        this.feeMax = feeMax;
        this.feeUnit = feeUnit;
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Makes the profile public (FR-T8).
     *
     * @throws BusinessRuleViolationException if anything {@link #missingFields()} lists is
     *                                        still missing, or the profile is suspended
     */
    public void publish() {
        ensureEditable();
        List<ProfileField> missing = missingFields();
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException(ERROR_PROFILE_INCOMPLETE,
                    "Profile %s cannot be published; still missing: %s".formatted(getId(), missing));
        }
        this.status = ProfileStatus.PUBLISHED;
    }

    /**
     * Takes the profile down at the request of its owner (FR-T8). The content is kept, so
     * publishing again is one call rather than a re-entry of the whole wizard.
     *
     * @throws BusinessRuleViolationException if the profile is not currently published
     */
    public void unpublish() {
        ensureEditable();
        if (status != ProfileStatus.PUBLISHED) {
            throw new BusinessRuleViolationException(ERROR_PROFILE_NOT_PUBLISHED,
                    "Profile %s is %s and cannot be unpublished".formatted(getId(), status));
        }
        this.status = ProfileStatus.UNPUBLISHED;
    }

    /** Administrator action: takes the profile down and freezes it. Reversed by {@link #reinstate()}. */
    public void suspend() {
        this.status = ProfileStatus.SUSPENDED;
    }

    /** Administrator action: lifts a suspension, handing the profile back as an editable draft. */
    public void reinstate() {
        if (status != ProfileStatus.SUSPENDED) {
            return;
        }
        this.status = ProfileStatus.DRAFT;
    }

    /**
     * Administrator action after the document review (FR-R3, Phase 6). Deliberately separate
     * from publishing: a published profile is one the tutor finished, a verified one is one we
     * checked, and conflating them would put a badge on an unchecked profile.
     */
    public void verify(Instant at) {
        this.verified = true;
        this.verifiedAt = requireNotNull(at, "at");
    }

    /** Withdraws the badge - a document expired, or the review was reversed. */
    public void revokeVerification() {
        this.verified = false;
        this.verifiedAt = null;
    }

    // ---------------------------------------------------------------------
    // The completeness rule, read both ways
    // ---------------------------------------------------------------------

    /** True when {@link #publish()} would succeed. */
    public boolean isComplete() {
        return missingFields().isEmpty();
    }

    /**
     * Everything publishing still needs, in wizard order, so a client can point at the step
     * that is short rather than say incomplete.
     *
     * <p>This and {@link #publish()} are the same rule: publish refuses exactly when this
     * returns anything, so a green progress bar and a rejected publish cannot coexist.
     */
    public List<ProfileField> missingFields() {
        List<ProfileField> missing = new ArrayList<>();
        if (isBlank(photoUrl)) {
            missing.add(ProfileField.PHOTO);
        }
        if (isBlank(bio)) {
            missing.add(ProfileField.BIO);
        }
        if (subjects.isEmpty()) {
            missing.add(ProfileField.SUBJECTS);
        }
        if (examLevels.isEmpty()) {
            missing.add(ProfileField.EXAM_LEVELS);
        }
        if (mediums.isEmpty()) {
            missing.add(ProfileField.MEDIUMS);
        }
        if (classFormats.isEmpty()) {
            missing.add(ProfileField.CLASS_FORMATS);
        }
        if (feeMin == null || feeMax == null || feeUnit == null) {
            missing.add(ProfileField.FEE_RANGE);
        }
        // Reachable somewhere: in person in at least one area, or online. A tutor who is
        // neither cannot be found by anybody, whatever else the profile says.
        if (areasServed.isEmpty() && !availableOnline) {
            missing.add(ProfileField.LOCATION);
        }
        return List.copyOf(missing);
    }

    /** True only for the live, publicly visible state. */
    public boolean isPublished() {
        return status == ProfileStatus.PUBLISHED;
    }

    /** Ownership check for the rule that a tutor edits only their own profile. */
    public boolean belongsTo(Long userId) {
        return userId != null && Objects.equals(tutor.getId(), userId);
    }

    // Read-only views: these collections are replaced through the wizard methods above,
    // never mutated by a caller that happens to be holding a reference.

    public Set<Subject> getSubjects() {
        return Collections.unmodifiableSet(subjects);
    }

    public Set<ExamLevel> getExamLevels() {
        return Collections.unmodifiableSet(examLevels);
    }

    public Set<Syllabus> getSyllabuses() {
        return Collections.unmodifiableSet(syllabuses);
    }

    public Set<Area> getAreasServed() {
        return Collections.unmodifiableSet(areasServed);
    }

    public Set<Medium> getMediums() {
        return Collections.unmodifiableSet(mediums);
    }

    public Set<ClassFormat> getClassFormats() {
        return Collections.unmodifiableSet(classFormats);
    }

    public List<Qualification> getQualifications() {
        return Collections.unmodifiableList(qualifications);
    }

    /**
     * A suspended profile is frozen: a tutor may not edit their way out of a moderation
     * decision, and only {@link #reinstate()} lifts it.
     */
    private void ensureEditable() {
        if (status == ProfileStatus.SUSPENDED) {
            throw new BusinessRuleViolationException(ERROR_PROFILE_SUSPENDED,
                    "Profile %s has been suspended and cannot be changed".formatted(getId()));
        }
    }

    /**
     * Replaces a collection in place. Clearing and refilling rather than assigning a new
     * instance is what lets Hibernate work out the difference and issue the deletes and
     * inserts; a fresh collection would orphan the one it is tracking.
     */
    private static <T> void replace(Collection<T> target, Collection<T> replacement) {
        target.clear();
        if (replacement != null) {
            replacement.stream().filter(Objects::nonNull).forEach(target::add);
        }
    }

    private static void requireNotNegative(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("%s must not be negative, was %s".formatted(field, value));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
