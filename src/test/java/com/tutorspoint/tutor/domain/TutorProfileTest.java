package com.tutorspoint.tutor.domain;

import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The publish rule, exercised where it lives.
 *
 * <p>No Spring, no database and no mocks: everything under test here is a decision the entity
 * makes on its own, and a test that needed a container to ask "may this be published" would be
 * evidence the rule had leaked into a service.
 *
 * <p>The centrepiece is {@link MissingFields}, which removes one requirement at a time from an
 * otherwise complete profile. That is the enumeration the plan asks for, and it is what pins
 * {@code missingFields()} and {@code publish()} to each other: every case asserts both the list
 * the wizard would show and the refusal the publish button would get.
 */
class TutorProfileTest {

    private static final BigDecimal FEE_MIN = new BigDecimal("1500.00");
    private static final BigDecimal FEE_MAX = new BigDecimal("2500.00");

    @Nested
    @DisplayName("a new profile")
    class NewProfile {

        @Test
        @DisplayName("starts as an accepting draft with nothing filled in")
        void startsEmpty() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DRAFT);
            assertThat(profile.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ACCEPTING);
            assertThat(profile.isAvailableOnline()).isFalse();
            assertThat(profile.isVerified()).isFalse();
            assertThat(profile.getVerifiedAt()).isNull();
            assertThat(profile.isComplete()).isFalse();
        }

        @Test
        @DisplayName("lists every requirement, so the wizard can draw a full progress bar")
        void listsEveryRequirement() {
            assertThat(new TutorProfile(tutor()).missingFields())
                    .containsExactly(ProfileField.values());
        }

        @Test
        @DisplayName("refuses to exist without an owner")
        void needsAnOwner() {
            assertThatIllegalArgumentException().isThrownBy(() -> new TutorProfile(null));
        }
    }

    @Nested
    @DisplayName("publishing a complete profile")
    class Publishing {

        @Test
        @DisplayName("goes live and reports itself complete")
        void publishesWhenComplete() {
            TutorProfile profile = complete();

            assertThat(profile.isComplete()).isTrue();
            assertThat(profile.missingFields()).isEmpty();

            profile.publish();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.PUBLISHED);
            assertThat(profile.isPublished()).isTrue();
        }

        @Test
        @DisplayName("does not award the verified badge - that is an administrator decision")
        void publishingDoesNotVerify() {
            TutorProfile profile = complete();

            profile.publish();

            assertThat(profile.isVerified()).isFalse();
            assertThat(profile.getVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("is satisfied by online availability alone, with no area served")
        void onlineOnlyTutorIsReachable() {
            TutorProfile profile = complete();
            profile.servesAreas(null, Set.of(), null);
            profile.delivers(Set.of(ClassFormat.ONLINE), true, AvailabilityStatus.ACCEPTING);

            assertThat(profile.missingFields()).isEmpty();
            assertThatCode(profile::publish).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("an incomplete profile")
    class MissingFields {

        @Test
        @DisplayName("without a photo cannot be published")
        void photo() {
            assertMissing(ProfileField.PHOTO,
                    profile -> profile.describe("A/L Chemistry", "Fifteen years of A/L Chemistry.", null, null));
        }

        @Test
        @DisplayName("without a bio cannot be published")
        void bio() {
            assertMissing(ProfileField.BIO,
                    profile -> profile.describe("A/L Chemistry", "   ", "https://cdn.example.lk/p.jpg", null));
        }

        @Test
        @DisplayName("without a subject cannot be published")
        void subjects() {
            assertMissing(ProfileField.SUBJECTS,
                    profile -> profile.teaches(Set.of(), Set.of(examLevel()), Set.of(syllabus()),
                            Set.of(Medium.ENGLISH)));
        }

        @Test
        @DisplayName("without an exam level cannot be published")
        void examLevels() {
            assertMissing(ProfileField.EXAM_LEVELS,
                    profile -> profile.teaches(Set.of(subject()), Set.of(), Set.of(syllabus()),
                            Set.of(Medium.ENGLISH)));
        }

        @Test
        @DisplayName("without a medium cannot be published")
        void mediums() {
            assertMissing(ProfileField.MEDIUMS,
                    profile -> profile.teaches(Set.of(subject()), Set.of(examLevel()), Set.of(syllabus()), Set.of()));
        }

        @Test
        @DisplayName("without a class format cannot be published")
        void classFormats() {
            assertMissing(ProfileField.CLASS_FORMATS,
                    profile -> profile.delivers(Set.of(), false, AvailabilityStatus.ACCEPTING));
        }

        @Test
        @DisplayName("without a fee range cannot be published")
        void feeRange() {
            assertMissing(ProfileField.FEE_RANGE,
                    profile -> profile.chargesBetween(null, null, null));
        }

        @Test
        @DisplayName("reachable neither in person nor online cannot be published")
        void location() {
            assertMissing(ProfileField.LOCATION, profile -> {
                profile.servesAreas(null, Set.of(), null);
                profile.delivers(Set.of(ClassFormat.ONE_TO_ONE), false, AvailabilityStatus.ACCEPTING);
            });
        }

        @Test
        @DisplayName("names every outstanding field at once, in wizard order")
        void reportsAllOfThemTogether() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThat(profile.missingFields()).containsExactly(
                    ProfileField.PHOTO,
                    ProfileField.BIO,
                    ProfileField.SUBJECTS,
                    ProfileField.EXAM_LEVELS,
                    ProfileField.MEDIUMS,
                    ProfileField.CLASS_FORMATS,
                    ProfileField.FEE_RANGE,
                    ProfileField.LOCATION);
        }

        /**
         * Takes a complete profile, breaks exactly one requirement, and asserts the two halves
         * of the rule agree: the field is listed, and publishing is refused for it.
         */
        private void assertMissing(ProfileField expected, Consumer<TutorProfile> damage) {
            TutorProfile profile = complete();
            damage.accept(profile);

            assertThat(profile.missingFields()).containsExactly(expected);
            assertThat(profile.isComplete()).isFalse();

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(profile::publish)
                    .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("PROFILE_INCOMPLETE"))
                    .withMessageContaining(expected.name());

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("unpublishing")
    class Unpublishing {

        @Test
        @DisplayName("takes a live profile down and keeps its content")
        void unpublishesAPublishedProfile() {
            TutorProfile profile = complete();
            profile.publish();

            profile.unpublish();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.UNPUBLISHED);
            assertThat(profile.isPublished()).isFalse();
            assertThat(profile.getBio()).isNotBlank();
            assertThat(profile.isComplete()).isTrue();
        }

        @Test
        @DisplayName("lets the tutor go live again without re-entering anything")
        void republishesFromUnpublished() {
            TutorProfile profile = complete();
            profile.publish();
            profile.unpublish();

            profile.publish();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.PUBLISHED);
        }

        @ParameterizedTest
        @EnumSource(value = ProfileStatus.class, names = {"DRAFT", "UNPUBLISHED"})
        @DisplayName("is refused on a profile that is not live")
        void refusedWhenNotPublished(ProfileStatus status) {
            TutorProfile profile = complete();
            if (status == ProfileStatus.UNPUBLISHED) {
                profile.publish();
                profile.unpublish();
            }

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(profile::unpublish)
                    .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("PROFILE_NOT_PUBLISHED"));
        }
    }

    @Nested
    @DisplayName("a suspended profile")
    class Suspended {

        @Test
        @DisplayName("cannot be edited or published by its owner")
        void isFrozen() {
            TutorProfile profile = complete();
            profile.publish();

            profile.suspend();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.SUSPENDED);
            assertThat(profile.isPublished()).isFalse();
            assertRefusedAsSuspended(profile::publish);
            assertRefusedAsSuspended(profile::unpublish);
            assertRefusedAsSuspended(() -> profile.describe("New", "New bio", "https://cdn.example.lk/p.jpg", null));
            assertRefusedAsSuspended(() -> profile.recordExperience(20));
        }

        @Test
        @DisplayName("comes back as a draft when an administrator lifts the suspension")
        void reinstatesAsDraft() {
            TutorProfile profile = complete();
            profile.publish();
            profile.suspend();

            profile.reinstate();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DRAFT);
            assertThatCode(profile::publish).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("is the only state reinstating touches")
        void reinstatingIsANoOpElsewhere() {
            TutorProfile profile = complete();
            profile.publish();

            profile.reinstate();

            assertThat(profile.getStatus()).isEqualTo(ProfileStatus.PUBLISHED);
        }

        private void assertRefusedAsSuspended(Runnable action) {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(action::run)
                    .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("PROFILE_SUSPENDED"));
        }
    }

    @Nested
    @DisplayName("the fee range")
    class Fees {

        @Test
        @DisplayName("refuses a minimum above the maximum")
        void refusesAnInvertedRange() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> profile.chargesBetween(FEE_MAX, FEE_MIN, FeeUnit.PER_HOUR));
        }

        @Test
        @DisplayName("refuses a negative fee")
        void refusesANegativeFee() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> profile.chargesBetween(new BigDecimal("-1"), FEE_MAX, FeeUnit.PER_HOUR));
        }

        @Test
        @DisplayName("refuses an amount with no unit, because it is not comparable")
        void refusesAPartialRange() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> profile.chargesBetween(FEE_MIN, FEE_MAX, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> profile.chargesBetween(FEE_MIN, null, FeeUnit.PER_MONTH));
        }

        @Test
        @DisplayName("accepts a single price as a range of one value")
        void acceptsAFlatFee() {
            TutorProfile profile = new TutorProfile(tutor());

            profile.chargesBetween(FEE_MIN, FEE_MIN, FeeUnit.PER_MONTH);

            assertThat(profile.getFeeMin()).isEqualByComparingTo(FEE_MIN);
            assertThat(profile.getFeeMax()).isEqualByComparingTo(FEE_MIN);
            assertThat(profile.missingFields()).doesNotContain(ProfileField.FEE_RANGE);
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("replaces a collection rather than adding to it")
        void collectionsAreReplaced() {
            TutorProfile profile = complete();

            profile.teaches(Set.of(subject()), Set.of(examLevel()), Set.of(), Set.of(Medium.SINHALA));

            assertThat(profile.getSyllabuses()).isEmpty();
            assertThat(profile.getMediums()).containsExactly(Medium.SINHALA);
        }

        @Test
        @DisplayName("treats a null collection as an empty one, so a partial draft can be saved")
        void nullCollectionsAreEmpty() {
            TutorProfile profile = complete();

            profile.teaches(null, null, null, null);

            assertThat(profile.getSubjects()).isEmpty();
            assertThat(profile.getExamLevels()).isEmpty();
            assertThat(profile.getMediums()).isEmpty();
        }

        @Test
        @DisplayName("does not list the same qualification twice")
        void qualificationsAreDeduplicated() {
            TutorProfile profile = new TutorProfile(tutor());
            Qualification degree = new Qualification("BSc Chemistry", "University of Colombo", 2008);

            profile.listQualifications(List.of(degree, new Qualification("BSc Chemistry", "University of Colombo", 2008)));

            assertThat(profile.getQualifications()).containsExactly(degree);
        }

        @Test
        @DisplayName("keeps qualifications in the order the tutor arranged them")
        void qualificationsKeepTheirOrder() {
            TutorProfile profile = new TutorProfile(tutor());
            Qualification degree = new Qualification("BSc Chemistry", "University of Colombo", 2008);
            Qualification diploma = new Qualification("PGDE", "NIE", 2011);

            profile.listQualifications(List.of(diploma, degree));

            assertThat(profile.getQualifications()).containsExactly(diploma, degree);
        }

        @Test
        @DisplayName("refuses a negative travel radius or a negative number of years")
        void refusesNegativeNumbers() {
            TutorProfile profile = new TutorProfile(tutor());

            assertThatIllegalArgumentException().isThrownBy(() -> profile.servesAreas(null, Set.of(), -1));
            assertThatIllegalArgumentException().isThrownBy(() -> profile.recordExperience(-1));
        }

        @Test
        @DisplayName("hands out collections a caller cannot modify")
        void collectionsAreReadOnly() {
            TutorProfile profile = complete();

            assertThat(profile.getSubjects()).isUnmodifiable();
            assertThat(profile.getMediums()).isUnmodifiable();
            assertThat(profile.getQualifications()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("the verified badge")
    class Verification {

        @Test
        @DisplayName("is stamped with the moment it was awarded")
        void verifyRecordsWhen() {
            TutorProfile profile = new TutorProfile(tutor());
            Instant at = Instant.parse("2026-03-01T09:00:00Z");

            profile.verify(at);

            assertThat(profile.isVerified()).isTrue();
            assertThat(profile.getVerifiedAt()).isEqualTo(at);
        }

        @Test
        @DisplayName("leaves no date behind when it is withdrawn")
        void revokeClearsTheDate() {
            TutorProfile profile = new TutorProfile(tutor());
            profile.verify(Instant.parse("2026-03-01T09:00:00Z"));

            profile.revokeVerification();

            assertThat(profile.isVerified()).isFalse();
            assertThat(profile.getVerifiedAt()).isNull();
        }

        @Test
        @DisplayName("is independent of publishing, so a suspended profile keeps it")
        void survivesSuspension() {
            TutorProfile profile = complete();
            profile.verify(Instant.parse("2026-03-01T09:00:00Z"));

            profile.suspend();

            assertThat(profile.isVerified()).isTrue();
        }
    }

    @Test
    @DisplayName("belongs only to the tutor it was created for")
    void ownershipIsByTutorId() {
        TutorProfile profile = new TutorProfile(tutor());

        // Neither id is set outside a persistence context, so the honest assertion is the one
        // that matters for authorization: an unsaved profile matches nobody.
        assertThat(profile.belongsTo(null)).isFalse();
        assertThat(profile.belongsTo(42L)).isFalse();
    }

    /** A profile with every one of the eight requirements satisfied. */
    private static TutorProfile complete() {
        TutorProfile profile = new TutorProfile(tutor());
        profile.describe("A/L Chemistry in Nugegoda",
                "Fifteen years preparing students for A/L Chemistry.",
                "https://cdn.example.lk/photos/kasun.jpg",
                null);
        profile.teaches(Set.of(subject()), Set.of(examLevel()), Set.of(syllabus()), Set.of(Medium.ENGLISH));
        profile.delivers(Set.of(ClassFormat.SMALL_GROUP), false, AvailabilityStatus.LIMITED);
        profile.servesAreas(area(), Set.of(area()), 15);
        profile.listQualifications(List.of(new Qualification("BSc Chemistry", "University of Colombo", 2008)));
        profile.recordExperience(15);
        profile.chargesBetween(FEE_MIN, FEE_MAX, FeeUnit.PER_MONTH);
        return profile;
    }

    private static Tutor tutor() {
        return new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN);
    }

    private static Subject subject() {
        return new Subject("CHEMISTRY", 1);
    }

    private static ExamLevel examLevel() {
        return new ExamLevel("GCE_AL", 1);
    }

    private static Syllabus syllabus() {
        return new Syllabus("NATIONAL_ENGLISH", 1);
    }

    private static Area area() {
        return Area.district("COLOMBO", 1, new BigDecimal("6.927079"), new BigDecimal("79.861244"));
    }
}
