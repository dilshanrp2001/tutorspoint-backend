package com.tutorspoint.tutor.seed;

import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileStorage;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.MediaUrls;
import com.tutorspoint.common.storage.StorageArea;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.ReferenceEntity;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;
import com.tutorspoint.tutor.domain.Qualification;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates demo tutors with complete, plausible profiles - enough volume to exercise search
 * and to hold it to the NFR-1 budget (Phase 3).
 *
 * <p>Dev and test only. Everything goes through the real entities: accounts are verified and
 * activated, profiles are filled in wizard step by wizard step and published through
 * {@link TutorProfile#publish()}, so a seeded profile satisfies exactly the invariants a real one
 * does and a broken rule fails the seed rather than hiding in fixture SQL.
 *
 * <p>"Plausible" is specific. A tutor teaches a coherent specialism (A/L Commerce, not Chemistry
 * with Japanese), the syllabus follows the medium, fees follow the format, towns come from one
 * district, names follow the medium, and the pilot districts are weighted the way the pilot is.
 * Seeded from a fixed number, so two runs produce the same directory.
 *
 * <p>Accounts use {@code @}{@value #EMAIL_DOMAIN} addresses and the password
 * {@value #DEMO_PASSWORD}, so a developer can sign in as any of them.
 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DemoTutorSeeder {

    public static final String EMAIL_DOMAIN = "demo.tutorspoint.lk";
    public static final String DEMO_PASSWORD = "DemoTutor2026";

    private static final List<Specialism> SPECIALISMS = List.of(
            new Specialism(List.of("GRADE_5_SCHOLARSHIP"), List.of("MATHEMATICS", "ENGLISH_LANGUAGE", "SCIENCE"), 2, Track.NATIONAL),
            new Specialism(List.of("GRADE_6_TO_9", "GCE_OL"), List.of("MATHEMATICS", "SCIENCE"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_OL"), List.of("BUSINESS_AND_ACCOUNTING_STUDIES", "HISTORY", "GEOGRAPHY", "CIVIC_EDUCATION", "ICT"), 2, Track.NATIONAL),
            new Specialism(List.of("GRADE_6_TO_9", "GCE_OL"), List.of("ENGLISH_LANGUAGE", "ENGLISH_LITERATURE"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_AL"), List.of("COMBINED_MATHEMATICS", "PHYSICS", "CHEMISTRY"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_AL"), List.of("BIOLOGY", "CHEMISTRY", "PHYSICS", "AGRICULTURAL_SCIENCE"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_AL"), List.of("ACCOUNTING", "ECONOMICS", "BUSINESS_STUDIES", "BUSINESS_STATISTICS"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_AL"), List.of("POLITICAL_SCIENCE", "LOGIC_AND_SCIENTIFIC_METHOD", "GEOGRAPHY", "BUDDHIST_CIVILIZATION"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_AL"), List.of("ENGINEERING_TECHNOLOGY", "SCIENCE_FOR_TECHNOLOGY", "BIOSYSTEMS_TECHNOLOGY"), 2, Track.NATIONAL),
            new Specialism(List.of("GCE_OL", "GCE_AL"), List.of("ICT"), 1, Track.NATIONAL),
            new Specialism(List.of("GCE_OL", "GCE_AL"), List.of("MATHEMATICS", "PHYSICS", "CHEMISTRY", "BIOLOGY"), 2, Track.INTERNATIONAL),
            new Specialism(List.of("PROFESSIONAL"), List.of("CIMA", "ACCA", "AAT", "CA_SRI_LANKA"), 2, Track.PROFESSIONAL),
            new Specialism(List.of("LANGUAGE_AND_SKILLS"), List.of("SPOKEN_ENGLISH", "IELTS"), 2, Track.SKILLS),
            new Specialism(List.of("LANGUAGE_AND_SKILLS"), List.of("JAPANESE", "KOREAN", "FRENCH", "GERMAN", "CHINESE"), 1, Track.SKILLS),
            new Specialism(List.of("LANGUAGE_AND_SKILLS", "UNDERGRADUATE"), List.of("COMPUTER_PROGRAMMING", "GRAPHIC_DESIGN"), 1, Track.SKILLS));

    private static final List<String> SINHALA_GIVEN = List.of("Kasun", "Nimal", "Sanduni", "Dilini", "Chamara",
            "Tharindu", "Ishara", "Nadeesha", "Ruwan", "Hasini", "Pradeep", "Gayani", "Lahiru", "Shehani",
            "Buddhika", "Madushani", "Chathura", "Anjali", "Supun", "Nirmala");
    private static final List<String> SINHALA_FAMILY = List.of("Perera", "Fernando", "Silva", "Jayasinghe",
            "Wickramasinghe", "Bandara", "Rathnayake", "Gunawardena", "Dissanayake", "Senanayake", "Herath",
            "Karunaratne", "Wijesekara", "Amarasinghe", "Samarawickrama");
    private static final List<String> TAMIL_GIVEN = List.of("Kumaran", "Tharshini", "Sivakumar", "Nirosha",
            "Pradeepan", "Kavitha", "Rajeevan", "Thanuja", "Vithusan", "Janani", "Mohamed", "Fathima", "Rizwan",
            "Ayesha");
    private static final List<String> TAMIL_FAMILY = List.of("Sivarajah", "Thevarajah", "Kanagaratnam",
            "Selvanayagam", "Rajendran", "Balasubramaniam", "Arulampalam", "Farook", "Marikkar", "Cassim", "Hameed");
    private static final List<String> OTHER_GIVEN = List.of("Andrew", "Michelle", "Kevin", "Natasha", "Dinesh",
            "Shalini", "Roshan", "Priyanka");
    private static final List<String> OTHER_FAMILY = List.of("de Kretser", "Van Dort", "Ondaatje", "Jansz",
            "Mendis", "de Silva", "Rodrigo");

    private static final List<String> UNIVERSITIES = List.of("University of Colombo", "University of Peradeniya",
            "University of Kelaniya", "University of Sri Jayewardenepura", "University of Moratuwa",
            "Open University of Sri Lanka", "University of Ruhuna");

    private static final Color[] AVATAR_BACKGROUNDS = {
            new Color(0x1E5AA8), new Color(0x2E7D32), new Color(0x8E24AA), new Color(0xC62828),
            new Color(0x00838F), new Color(0xEF6C00), new Color(0x5D4037), new Color(0x3949AB)};

    private final UserRepository users;
    private final TutorProfileRepository profiles;
    private final SubjectRepository subjects;
    private final ExamLevelRepository examLevels;
    private final SyllabusRepository syllabuses;
    private final AreaRepository areas;
    private final PasswordEncoder passwordEncoder;
    private final FileStorage fileStorage;
    private final Clock clock;

    /** The address of the n-th demo tutor, from 1. */
    public static String emailFor(int n) {
        return "tutor%04d@%s".formatted(n, EMAIL_DOMAIN);
    }

    /** True once the first demo tutor exists - the runner's cue to leave the database alone. */
    @Transactional(readOnly = true)
    public boolean alreadySeeded() {
        return users.existsByEmail(emailFor(1));
    }

    /**
     * Creates {@code count} demo tutors, numbered from 1, in one transaction.
     *
     * <p>Most are published. A few are left as drafts and a few are unpublished, as in a real
     * directory - which also gives search something it must not show.
     *
     * @return how many of them were published
     */
    @Transactional
    public int seed(int count, long randomSeed) {
        Random random = new Random(randomSeed);
        Vocabulary vocabulary = loadVocabulary();
        // One hash for everybody: BCrypt is slow by design, and five hundred of them would turn
        // a seconds-long seed into a minutes-long one for no security benefit on demo accounts.
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        int published = 0;
        for (int n = 1; n <= count; n++) {
            if (seedOne(n, random, vocabulary, passwordHash)) {
                published++;
            }
        }
        log.info("Seeded {} demo tutors ({} published)", count, published);
        return published;
    }

    private boolean seedOne(int n, Random random, Vocabulary vocabulary, String passwordHash) {
        Specialism specialism = pick(random, SPECIALISMS);
        Medium medium = specialism.track().pickMedium(random);
        String fullName = name(random, medium);

        Tutor tutor = new Tutor(emailFor(n), passwordHash, fullName, "+94779%06d".formatted(n), languageOf(medium));
        tutor.verifyEmail();
        tutor.verifyPhone();
        tutor.activate();
        users.save(tutor);

        TutorProfile profile = profiles.save(new TutorProfile(tutor));

        List<Subject> taught = pickSome(random, specialism.subjects(), 1, specialism.maxSubjects()).stream()
                .map(vocabulary.subjects()::get).toList();
        List<ExamLevel> levels = specialism.examLevels().stream().map(vocabulary.examLevels()::get).toList();
        int years = 1 + (int) Math.round(Math.pow(random.nextDouble(), 1.6) * 29);

        boolean onlineOnly = random.nextDouble() < 0.12;
        boolean online = onlineOnly || random.nextDouble() < 0.35;
        Area homeBase = onlineOnly ? null : pickHomeTown(random, vocabulary.districts());
        Set<Area> served = onlineOnly ? Set.of() : areasServed(random, homeBase);

        Set<ClassFormat> formats = formats(random, specialism.track(), online, onlineOnly);
        ClassFormat mainFormat = formats.stream().filter(format -> format != ClassFormat.ONLINE).findFirst()
                .orElse(ClassFormat.ONE_TO_ONE);

        profile.describe(headline(taught, levels, homeBase, years, medium), bio(random, taught, levels, homeBase, years, medium));
        profile.teaches(taught, levels, syllabusesFor(random, specialism.track(), medium, vocabulary.syllabuses()), Set.of(medium));
        profile.delivers(formats, online, pickWeighted(random,
                Map.of(AvailabilityStatus.ACCEPTING, 60, AvailabilityStatus.LIMITED, 30, AvailabilityStatus.FULL, 10)));
        profile.servesAreas(homeBase, served, onlineOnly ? null : 3 + random.nextInt(18));
        profile.listQualifications(qualifications(random, taught, specialism.track(), years, Year.now(clock).getValue()));
        profile.recordExperience(years);
        chargeFee(random, profile, specialism.track(), mainFormat);
        profile.attachPhoto(storeAvatar(fullName, n));

        if (random.nextDouble() < 0.35) {
            profile.verify(Instant.now(clock).minus(Duration.ofDays(1 + random.nextInt(300))));
        }
        if (random.nextDouble() < 0.65) {
            int reviews = 1 + random.nextInt(60);
            // 3.2 to 5.0 in tenths: parents review the tutors they stay with.
            BigDecimal average = BigDecimal.valueOf(32 + random.nextInt(19), 1);
            profile.summariseReviews(average, reviews);
        }

        double lifecycle = random.nextDouble();
        if (lifecycle < 0.06) {
            return false; // left as a complete but unpublished draft
        }
        profile.publish();
        if (lifecycle < 0.09) {
            profile.unpublish();
            return false;
        }
        return true;
    }

    private Vocabulary loadVocabulary() {
        return new Vocabulary(
                byCode(subjects.findActiveWithTranslations()),
                byCode(examLevels.findActiveWithTranslations()),
                byCode(syllabuses.findActiveWithTranslations()),
                areas.findActiveDistrictsWithTowns());
    }

    // ---------------------------------------------------------------------
    // Who, where, what, how much
    // ---------------------------------------------------------------------

    private static String name(Random random, Medium medium) {
        return switch (medium) {
            case SINHALA -> pick(random, SINHALA_GIVEN) + " " + pick(random, SINHALA_FAMILY);
            case TAMIL -> pick(random, TAMIL_GIVEN) + " " + pick(random, TAMIL_FAMILY);
            case ENGLISH -> switch (random.nextInt(4)) {
                case 0 -> pick(random, TAMIL_GIVEN) + " " + pick(random, TAMIL_FAMILY);
                case 1 -> pick(random, OTHER_GIVEN) + " " + pick(random, OTHER_FAMILY);
                default -> pick(random, SINHALA_GIVEN) + " " + pick(random, SINHALA_FAMILY);
            };
        };
    }

    private static Language languageOf(Medium medium) {
        return switch (medium) {
            case SINHALA -> Language.SI;
            case TAMIL -> Language.TA;
            case ENGLISH -> Language.EN;
        };
    }

    /** Weighted the way the pilot is: Colombo first, then Gampaha, then Kalutara. */
    private static Area pickHomeTown(Random random, List<Area> districts) {
        Map<Area, Integer> weights = districts.stream().collect(Collectors.toMap(Function.identity(),
                district -> switch (district.getCode()) {
                    case "COLOMBO" -> 55;
                    case "GAMPAHA" -> 30;
                    default -> 15;
                }));
        Area district = pickWeighted(random, weights);
        return pick(random, List.copyOf(district.getTowns()));
    }

    /** The home town, a few neighbours from the same district, and now and then the whole district. */
    private static Set<Area> areasServed(Random random, Area homeTown) {
        Set<Area> served = new LinkedHashSet<>();
        served.add(homeTown);
        Area district = homeTown.getParent();
        if (random.nextDouble() < 0.1) {
            served.add(district);
        }
        served.addAll(pickSome(random, List.copyOf(district.getTowns()), 0, 3));
        return served;
    }

    private static Collection<Syllabus> syllabusesFor(Random random, Track track, Medium medium,
                                                      Map<String, Syllabus> syllabuses) {
        List<String> codes = switch (track) {
            case NATIONAL -> random.nextDouble() < 0.15 && medium == Medium.ENGLISH
                    ? List.of("NATIONAL_ENGLISH", "CAMBRIDGE")
                    : List.of("NATIONAL_" + medium.name());
            case INTERNATIONAL -> pickSome(random, List.of("CAMBRIDGE", "EDEXCEL"), 1, 2);
            case PROFESSIONAL -> List.of("PROFESSIONAL_BODY");
            case SKILLS -> List.of("OTHER");
        };
        return codes.stream().map(syllabuses::get).toList();
    }

    private static Set<ClassFormat> formats(Random random, Track track, boolean online, boolean onlineOnly) {
        Set<ClassFormat> formats = new LinkedHashSet<>();
        if (!onlineOnly) {
            List<ClassFormat> inPerson = track == Track.NATIONAL
                    ? List.of(ClassFormat.ONE_TO_ONE, ClassFormat.SMALL_GROUP, ClassFormat.MASS_CLASS)
                    : List.of(ClassFormat.ONE_TO_ONE, ClassFormat.SMALL_GROUP);
            formats.addAll(pickSome(random, inPerson, 1, 2));
        }
        if (online) {
            formats.add(ClassFormat.ONLINE);
        }
        return formats;
    }

    /** Rupees, in hundreds, by how the tutor mainly teaches. */
    private static void chargeFee(Random random, TutorProfile profile, Track track, ClassFormat mainFormat) {
        FeeUnit unit;
        int low;
        int high;
        if (track == Track.SKILLS) {
            unit = FeeUnit.PER_CLASS;
            low = 1500;
            high = 4000;
        } else if (mainFormat == ClassFormat.ONE_TO_ONE) {
            unit = FeeUnit.PER_HOUR;
            low = track == Track.NATIONAL ? 1500 : 3000;
            high = track == Track.NATIONAL ? 5000 : 8000;
        } else if (mainFormat == ClassFormat.MASS_CLASS) {
            unit = FeeUnit.PER_MONTH;
            low = 1200;
            high = 3000;
        } else {
            unit = FeeUnit.PER_MONTH;
            low = 2000;
            high = 6000;
        }
        int min = (low + random.nextInt(high - low + 1)) / 100 * 100;
        int max = min + 500 * random.nextInt(4);
        profile.chargesBetween(BigDecimal.valueOf(min), BigDecimal.valueOf(max), unit);
    }

    private static List<Qualification> qualifications(Random random, List<Subject> taught, Track track, int years,
                                                      int currentYear) {
        int graduated = Math.max(1975, currentYear - years - random.nextInt(4));
        String field = taught.getFirst().nameIn(Language.EN);
        List<Qualification> qualifications = new ArrayList<>();
        switch (track) {
            case PROFESSIONAL -> qualifications.add(new Qualification(
                    "Passed finalist, " + field, field + " Sri Lanka", graduated));
            case SKILLS -> qualifications.add(new Qualification(
                    "Diploma in " + field, pick(random, List.of("British Council Colombo", "NIBM", "SLIIT", "IDM")), graduated));
            default -> qualifications.add(new Qualification(
                    "BSc (Special) in " + field, pick(random, UNIVERSITIES), graduated));
        }
        if (random.nextDouble() < 0.4) {
            qualifications.add(new Qualification("Postgraduate Diploma in Education",
                    "National Institute of Education", Math.min(currentYear, graduated + 2 + random.nextInt(4))));
        }
        return qualifications;
    }

    // ---------------------------------------------------------------------
    // Words
    // ---------------------------------------------------------------------

    private static String headline(List<Subject> taught, List<ExamLevel> levels, Area homeBase, int years, Medium medium) {
        String subjectNames = taught.stream().map(subject -> subject.nameIn(Language.EN)).collect(Collectors.joining(" & "));
        String where = homeBase == null ? "online" : homeBase.nameIn(Language.EN);
        String headline = "%s for %s - %d yrs, %s".formatted(subjectNames, levels.getLast().nameIn(Language.EN), years, where);
        if (medium != Medium.ENGLISH && headline.length() < 170) {
            headline += " (" + medium.name().charAt(0) + medium.name().substring(1).toLowerCase() + " medium)";
        }
        return headline.length() > 200 ? headline.substring(0, 200) : headline;
    }

    private static String bio(Random random, List<Subject> taught, List<ExamLevel> levels, Area homeBase, int years, Medium medium) {
        String subjectNames = taught.stream().map(subject -> subject.nameIn(Language.EN)).collect(Collectors.joining(" and "));
        StringBuilder bio = new StringBuilder()
                .append("I have taught ").append(subjectNames).append(" for ").append(years)
                .append(years == 1 ? " year" : " years").append(", preparing students for ")
                .append(levels.getLast().nameIn(Language.EN)).append(". ")
                .append(pick(random, List.of(
                        "Every class ends with past-paper practice under exam conditions.",
                        "I keep groups small so that every student gets individual attention.",
                        "Structured notes, weekly model papers and regular progress reports for parents.",
                        "I focus on understanding the concepts rather than memorising answers.")));
        if (homeBase != null) {
            bio.append(" Classes are held in ").append(homeBase.nameIn(Language.EN)).append(" and nearby.");
        }
        switch (medium) {
            case SINHALA -> bio.append(" සිසුන්ට විභාගය සඳහා හොඳින් සූදානම් වීමට උදව් කරමි.");
            case TAMIL -> bio.append(" மாணவர்கள் பரீட்சைக்கு நன்கு தயாராக உதவுகிறேன்.");
            case ENGLISH -> { }
        }
        return bio.toString();
    }

    /** A plain initials avatar, stored like an uploaded photo so the profile's photo URL resolves. */
    private String storeAvatar(String fullName, int n) {
        int size = 240;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(AVATAR_BACKGROUNDS[n % AVATAR_BACKGROUNDS.length]);
            graphics.fillRect(0, 0, size, size);
            String initials = List.of(fullName.split(" ")).stream()
                    .filter(part -> !part.isEmpty() && Character.isUpperCase(part.charAt(0)))
                    .limit(2).map(part -> part.substring(0, 1)).collect(Collectors.joining());
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 96));
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(initials, (size - metrics.stringWidth(initials)) / 2,
                    (size - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", jpeg);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode a demo avatar", e);
        }
        return MediaUrls.urlFor(fileStorage.store(StorageArea.PROFILE_PHOTOS, new FileContent(FileType.JPEG, jpeg.toByteArray())));
    }

    // ---------------------------------------------------------------------
    // Chance
    // ---------------------------------------------------------------------

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    /** Between {@code min} and {@code max} distinct values, in a random order. */
    private static <T> List<T> pickSome(Random random, List<T> values, int min, int max) {
        List<T> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled, random);
        int upper = Math.min(max, shuffled.size());
        int howMany = min + random.nextInt(Math.max(1, upper - min + 1));
        return List.copyOf(shuffled.subList(0, Math.min(howMany, upper)));
    }

    private static <T> T pickWeighted(Random random, Map<T, Integer> weights) {
        // Sorted by weight then string form, so a HashMap's iteration order cannot make two runs
        // with the same seed disagree.
        List<Map.Entry<T, Integer>> entries = weights.entrySet().stream()
                .sorted(Map.Entry.<T, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> String.valueOf(entry.getKey())))
                .toList();
        int roll = random.nextInt(entries.stream().mapToInt(Map.Entry::getValue).sum());
        for (Map.Entry<T, Integer> entry : entries) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return entries.getLast().getKey();
    }

    private static <T extends ReferenceEntity> Map<String, T> byCode(List<T> values) {
        return values.stream().collect(Collectors.toMap(ReferenceEntity::getCode, Function.identity(), (a, b) -> a));
    }

    private enum Track {
        NATIONAL, INTERNATIONAL, PROFESSIONAL, SKILLS;

        Medium pickMedium(Random random) {
            if (this != NATIONAL) {
                return Medium.ENGLISH;
            }
            double roll = random.nextDouble();
            return roll < 0.5 ? Medium.SINHALA : roll < 0.7 ? Medium.TAMIL : Medium.ENGLISH;
        }
    }

    private record Specialism(List<String> examLevels, List<String> subjects, int maxSubjects, Track track) {
    }

    private record Vocabulary(Map<String, Subject> subjects,
                              Map<String, ExamLevel> examLevels,
                              Map<String, Syllabus> syllabuses,
                              List<Area> districts) {
    }
}
