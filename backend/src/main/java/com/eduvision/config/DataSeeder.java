package com.eduvision.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.Assignment;
import com.eduvision.domain.AssignmentStatus;
import com.eduvision.domain.ClassMember;
import com.eduvision.domain.ClassSchedule;
import com.eduvision.domain.ClassSchedule.DayOfWeek;
import com.eduvision.domain.ContentPart;
import com.eduvision.domain.ContentStatus;
import com.eduvision.domain.EduClass;
import com.eduvision.domain.QuizQuestion;
import com.eduvision.domain.Role;
import com.eduvision.domain.Subject;
import com.eduvision.domain.User;
import com.eduvision.repository.ArContentRepository;
import com.eduvision.repository.AssignmentRepository;
import com.eduvision.repository.ClassMemberRepository;
import com.eduvision.repository.ClassScheduleRepository;
import com.eduvision.repository.ContentPartRepository;
import com.eduvision.repository.EduClassRepository;
import com.eduvision.repository.QuizQuestionRepository;
import com.eduvision.repository.UserRepository;
import com.eduvision.util.PasswordUtil;

/**
 * Seeds the demo dataset on a completely empty database.
 *
 * <p><b>Idempotency:</b> the whole seed is guarded by {@code userRepository.count() == 0}.
 * If even one user exists the runner logs and returns without touching anything, so a restart
 * against an existing database is always a no-op.
 *
 * <p>Creates 10 users, 1 class (+8 enrolments), 6 published AR contents backed by real online
 * Khronos glTF-Sample-Assets .glb URLs, 23 tappable model parts, 20 quiz questions and 2 assignments.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String MODEL_BASE = "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/";

    private final UserRepository userRepository;
    private final EduClassRepository eduClassRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ArContentRepository arContentRepository;
    private final ContentPartRepository contentPartRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassScheduleRepository classScheduleRepository;

    public DataSeeder(UserRepository userRepository,
                      EduClassRepository eduClassRepository,
                      ClassMemberRepository classMemberRepository,
                      ArContentRepository arContentRepository,
                      ContentPartRepository contentPartRepository,
                      QuizQuestionRepository quizQuestionRepository,
                      AssignmentRepository assignmentRepository,
                      ClassScheduleRepository classScheduleRepository) {
        this.userRepository = userRepository;
        this.eduClassRepository = eduClassRepository;
        this.classMemberRepository = classMemberRepository;
        this.arContentRepository = arContentRepository;
        this.contentPartRepository = contentPartRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.assignmentRepository = assignmentRepository;
        this.classScheduleRepository = classScheduleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existingUsers = userRepository.count();
        if (existingUsers > 0) {
            log.info("[SEED] User table already has {} row(s) - seed skipped (idempotent).", existingUsers);
            return;
        }
        log.info("[SEED] Empty database detected - seeding AR EduVision demo data ...");

        Instant now = Instant.now();

        // ---------------------------------------------------------------- users
        User teacher = user("Ms. Elena Fischer", "teacher@eduvision.com", "Teacher@123", Role.TEACHER, null);
        User developer = user("Dev Arjun Mehta", "developer@eduvision.com", "Developer@123", Role.DEVELOPER, null);

        String[] names = {"Ravi Sharma", "Priya Nair", "Aman Verma", "Zoya Khan",
                "Kabir Singh", "Meera Iyer", "Arjun Rao", "Nisha Patel"};
        List<User> students = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            students.add(user(names[i], "student" + (i + 1) + "@eduvision.com", "Student@123",
                    Role.STUDENT, "10"));
        }
        userRepository.save(teacher);
        userRepository.save(developer);
        userRepository.saveAll(students);
        log.info("[SEED] users={} (1 teacher, 1 developer, {} students)",
                userRepository.count(), students.size());

        // ---------------------------------------------------------------- class
        EduClass eduClass = new EduClass();
        eduClass.setName("Grade 10 Science - A");
        eduClass.setGrade("10");
        eduClass.setSection("A");
        eduClass.setTeacher(teacher);
        eduClass.setCreatedAt(now);
        eduClassRepository.save(eduClass);

        List<ClassMember> members = new ArrayList<>();
        for (User student : students) {
            ClassMember member = new ClassMember();
            member.setEduClass(eduClass);
            member.setStudent(student);
            member.setJoinedAt(now);
            members.add(member);
        }
        classMemberRepository.saveAll(members);
        log.info("[SEED] classes={} classMembers={}", eduClassRepository.count(), classMemberRepository.count());

        // ------------------------------------------------------------- contents
        ArContent helmet = content("Damaged Flight Helmet", Subject.HISTORY, "9",
                "A battle-scarred flight helmet from a cockpit — seven times bigger than the old placeholder "
                        + "models. In AR you can walk around it: the cracked visor, oxygen mask and ear-cups tell "
                        + "the story of early aviation safety. Tap each part to see how the design protected pilots.",
                MODEL_BASE + "DamagedHelmet/glTF-Binary/DamagedHelmet.glb", "ARU-HELMET-101", developer, now);

        ArContent camera = content("Antique Camera: Optics of Light", Subject.SCIENCE, "10",
                "A 17.5 MB, museum-grade antique camera for a physics lesson on lenses. The bellows, lens "
                        + "plate and film back show exactly how a real image forms. Place it on your desk in AR "
                        + "and tap the parts to explore the optics.",
                MODEL_BASE + "AntiqueCamera/glTF-Binary/AntiqueCamera.glb", "ARU-CAMERA-201", developer, now);

        ArContent fish = content("Barramundi Fish: Anatomy of a Fish", Subject.SCIENCE, "9",
                "A 12.5 MB anatomy specimen. Rotate the fish in AR and tap its gills, fins and swim bladder "
                        + "to learn why a fish is built the way it is — hydrodynamics you can point at.",
                MODEL_BASE + "BarramundiFish/glTF-Binary/BarramundiFish.glb", "ARU-FISH-301", developer, now);

        ArContent avocado = content("Avocado: Inside a Plant Cell", Subject.SCIENCE, "10",
                "Peel, flesh and seed — an avocado cross-section models the parts of a plant cell at desk "
                        + "scale. Zoom and tap each layer to connect it to what a biology exam actually asks about.",
                MODEL_BASE + "Avocado/glTF-Binary/Avocado.glb", "ARU-AVO-401", developer, now);

        ArContent car = content("Toy Car: Forces & Motion", Subject.MATHEMATICS, "10",
                "A 5.4 MB toy car that turns a forces lesson into something you can hold. Tap the wheels, "
                        + "chassis and axles to reason about friction, torque and why wheels roll instead of slide.",
                MODEL_BASE + "ToyCar/glTF-Binary/ToyCar.glb", "ARU-CAR-501", developer, now);

        ArContent boombox = content("Boombox: Sound Waves Explained", Subject.SCIENCE, "11",
                "A 10.6 MB boombox for the waves unit. The speaker grilles, cassette deck and antenna make "
                        + "good landmarks for a lesson on amplitude, frequency and how sound travels through air.",
                MODEL_BASE + "BoomBox/glTF-Binary/BoomBox.glb", "ARU-AUDIO-601", developer, now);

        arContentRepository.saveAll(List.of(helmet, camera, fish, avocado, car, boombox));
        log.info("[SEED] contents={} (all PUBLISHED, all Khronos glTF-Sample-Assets URLs)", arContentRepository.count());

        // ---------------------------------------------------------------- parts
        contentPartRepository.saveAll(List.of(
                // ---- Damaged Flight Helmet: 8 teachable parts
                part(helmet, 1, "shell", "Outer Shell",
                        "A hardened, crack-resistant shell is what stands between the pilot and debris. The \"damaged\" "
                                + "pitting on this example is real flight history - inspect it in AR and notice how the cracks "
                                + "branch instead of punching through, because the shell is meant to deform, not shatter."),
                part(helmet, 2, "visor", "Cracked Visor",
                        "The visor is a laminated transparency: even when the outer layer crazes, inner layers keep "
                                + "the pilot's view. The visible crack pattern is a textbook case of spreading impact "
                                + "energy across many laminated sheets."),
                part(helmet, 3, "oxygen_mask", "Oxygen Mask",
                        "At altitude there is barely enough oxygen to breathe, so the mask feeds the pilot from a "
                                + "pressure supply. Its seal has to stay airtight while the head moves and talks."),
                part(helmet, 4, "ear_cup_left", "Left Ear Cup",
                        "Twin speakers and microphones live inside. Comms headsets solve a real problem: in a roaring "
                                + "cockpit you cannot hear speech, so the ear cup must isolate and amplify."),
                part(helmet, 5, "ear_cup_right", "Right Ear Cup",
                        "Often the channel for the radio transmit microphone. Comparing left and right you can see "
                                + "how early headsets put different functions on each side."),
                part(helmet, 6, "chin_strap", "Chin Strap",
                        "A helmet that flies off at the first jolt does not protect anyone. The strap is sized for a "
                                + "quick-release buckle - one pull frees the pilot if the aircraft is inverted or sinking."),
                part(helmet, 7, "comm_port", "Comm Port",
                        "The cable connector plumbed directly to the aircraft's radio. It has to survive vibration and "
                                + "sweat for months without losing contact - a lesson in why aviation connectors are over-engineered."),
                part(helmet, 8, "liner", "Impact Liner",
                        "Inside the hard shell sits a soft crushable liner that lengthens the time of any impact, cutting "
                                + "peak G-forces on the skull. Same principle as a car's crumple zone, scaled to a head."),

                // ---- Antique Camera: 3
                part(camera, 1, "lens_assembly", "Lens Assembly",
                        "The lens bends light rays so they converge to a sharp image on the film. Its focal length sets "
                                + "how much of the scene fits in frame - short lenses see wide, long lenses zoom."),
                part(camera, 2, "bellows", "Bellows",
                        "The accordion tube that keeps light out while letting you move the lens back and forth. Pulling "
                                + "it out focuses on close subjects; pushing it in focuses at infinity."),
                part(camera, 3, "film_plate", "Film Plate",
                        "Where the image lands and is recorded. Film is coated in light-sensitive silver chemicals that "
                                + "darken where light falls - the same chemistry your theory textbooks call a latent image."),

                // ---- Barramundi Fish: 3
                part(fish, 1, "gills", "Gills",
                        "Fish do not breathe air; gills strip dissolved oxygen straight out of the water. Each thin "
                                + "filament gives a huge surface area - lose them and the fish suffocates within minutes."),
                part(fish, 2, "dorsal_fin", "Dorsal Fin",
                        "The upright fin on the back works like a keel on a boat: it stops the fish rolling over while "
                                + "it swims. Damage it, and the fish lists to one side."),
                part(fish, 3, "swim_bladder", "Swim Bladder",
                        "A gas-filled sac that acts as buoyancy control. Add gas and the fish rises; vent gas and it "
                                + "sinks - no constant swimming needed to hold depth."),

                // ---- Avocado: 3
                part(avocado, 1, "peel", "Peel (Cell Wall)",
                        "The tough dark skin is the plant cell's outer wall: it holds shape and takes the knocks of the "
                                + "world, just as a cell wall protects a plant cell's membrane."),
                part(avocado, 2, "flesh", "Flesh (Cytoplasm)",
                        "The rich green flesh is full of fats and enzymes - the food stores and workhorse chemistry of "
                                + "the cell, like cytoplasm packed with organelles."),
                part(avocado, 3, "seed", "Seed (Nucleus)",
                        "Every plant cell story comes back to the centre: the seed holds the DNA, the instructions for "
                                + "the next tree. It is the giant, comically oversized nucleus of this fruit."),

                // ---- Toy Car: 3
                part(car, 1, "wheels", "Wheels",
                        "Wheels convert a sliding rub into a rolling touch, slashing friction. Count the contact patches "
                                + "in AR and you have the whole reason carts exist."),
                part(car, 2, "chassis", "Chassis",
                        "The body-frame carries mass and decides how the car scales forces. Heavier chassis, slower "
                                + "acceleration for the same push - Newton's second law you can toy with."),
                part(car, 3, "axles", "Axles",
                        "A solid axle forces both wheels to turn together; independent axles let them differ. That "
                                + "choice is why karts understeer and sports cars do not."),

                // ---- Boombox: 3
                part(boombox, 1, "speaker_grille", "Speaker Grille",
                        "Behind the grille a cone pushes air back and forth. The frequency of that push is the pitch you "
                                + "hear; the size of it is the volume."),
                part(boombox, 2, "cassette_deck", "Cassette Deck",
                        "A spinning magnet-reading head turns a magnetised tape back into an electric signal, which the "
                                + "speaker then converts to sound again - a full round trip of energy forms."),
                part(boombox, 3, "antenna", "Antenna",
                        "A long conductor intercepts radio waves and turns them into a tiny current. Length matters: "
                                + "the best match comes at about a quarter of the wave's wavelength.")
        ));
        log.info("[SEED] parts={}", contentPartRepository.count());

        // ------------------------------------------------------------ questions
        List<QuizQuestion> questions = new ArrayList<>();

        // ---- Damaged Flight Helmet: exactly 5
        questions.addAll(questionsFor(helmet,
                new QSpec("Aviation history",
                        "Why is the helmet's shell intentionally designed to deform on impact?", "b",
                        "A deforming shell lengthens the impact and absorbs energy instead of passing the full "
                                + "jolt to the skull - the same idea as a car crumple zone.",
                        "So it looks more realistic", "To spread out and absorb impact energy",
                        "To make the helmet lighter", "To bend radio waves"),
                new QSpec("Materials",
                        "Why does cracked laminated visor glass often stay in one piece?", "d",
                        "Lamination sandwiches sheets together: cracks in one layer are stopped by the next, so "
                                + "the pilot keeps their field of view.",
                        "Because glass cannot crack", "Because it is hollow",
                        "Because the pilot is strong", "Because layers stop the crack from spreading"),
                new QSpec("Physiology",
                        "Why does a pilot at altitude need the oxygen mask inside the helmet?", "a",
                        "Air pressure drops with height, so there is not enough oxygen per breath. The mask "
                                + "supplies it from a tank.",
                        "The air no longer has enough oxygen to breathe", "The helmet makes oxygen",
                        "The wind is too cold", "It blocks engine noise only"),
                new QSpec("Comms",
                        "Why put speakers and microphones inside the ear cups?", "c",
                        "Cockpits are loud. Enclosing the ears isolates speech and blocks engine noise so the "
                                + "pilot can actually hear the radio.",
                        "To look symmetrical", "To hold the strap in place",
                        "To isolate speech from the roar of the engine", "To keep the ears warm"),
                new QSpec("Safety design",
                        "What is the point of a quick-release chin strap?", "c",
                        "One pull frees the helmet instantly - critical if the aircraft lands in water or is "
                                + "inverted.",
                        "It looks like a belt", "It adjusts temperature",
                        "It frees the pilot fast in an emergency", "It holds the oxygen tank")));

        // ---- Antique Camera: 3
        questions.addAll(questionsFor(camera,
                new QSpec("Optics",
                        "What does the lens of a camera do to incoming light?", "b",
                        "The lens bends (refracts) light rays so they converge to a sharp point on the film "
                                + "plane - that is how a real image is formed.",
                        "It colours the light", "It bends light to focus it",
                        "It blocks all light", "It turns light into sound"),
                new QSpec("Focus",
                        "What is the bellows for?", "a",
                        "The bellows let you move the lens relative to the film, which is how you focus. "
                                + "Close subject: pull the lens out. Far subject: push it in.",
                        "Adjusting lens-to-film distance to focus", "Carrying the film",
                        "Decorating the camera", "Zooming digitally"),
                new QSpec("Chemistry",
                        "Why does old film darken where light falls on it?", "d",
                        "Film is coated with light-sensitive silver salts. Light triggers a chemical change in "
                                + "those grains - that is the photograph.",
                        "Because it is painted black", "Because it rusts",
                        "Because it melts", "Because silver salts react to light")));

        // ---- Barramundi Fish: 3
        questions.addAll(questionsFor(fish,
                new QSpec("Respiration",
                        "Why do fish need gills instead of lungs?", "a",
                        "Gills extract dissolved oxygen directly from water. Lungs are built for breathing air, "
                                + "which has about 30 times more oxygen per litre.",
                        "Gills pull oxygen out of water where lungs cannot", "Fish have no blood",
                        "Lungs are too small", "Gills look better"),
                new QSpec("Stability",
                        "What job does the dorsal fin do?", "b",
                        "It acts like a boat's keel, stopping the fish from rolling side to side while swimming.",
                        "It makes the fish faster", "It keeps the fish upright",
                        "It steers the fish backwards", "It stores food"),
                new QSpec("Buoyancy",
                        "How does a swim bladder let a fish hold its depth?", "c",
                        "The fish adds or vents gas to match its average density to the water around it, so it "
                                + "neither sinks nor floats.",
                        "It pumps water in and out", "It flaps harder",
                        "It adjusts its gas volume", "It changes colour")));

        // ---- Avocado: 3
        questions.addAll(questionsFor(avocado,
                new QSpec("Cell wall",
                        "What does the avocado's tough skin do, like a plant cell wall?", "d",
                        "The skin is a rigid protective layer that keeps the soft inside safe and holds its "
                                + "shape - exactly what a cell wall does.",
                        "It makes the fruit sweeter", "It photosynthesises",
                        "It gives the fruit energy", "It protects and holds the shape of the inside"),
                new QSpec("Cytoplasm",
                        "The green flesh of the avocado is most like which part of a cell?", "b",
                        "It is the bulk of the cell, packed with nutrients and working chemistry - the "
                                + "cytoplasm role.",
                        "The nucleus", "The cytoplasm", "The cell membrane", "The vacuole"),
                new QSpec("Nucleus",
                        "The big seed in the middle of the avocado models which cell part?", "a",
                        "The seed holds the genetic instructions for the next plant - the nucleus role.",
                        "The nucleus", "The mitochondrion", "The ribosome", "The cell wall")));

        // ---- Toy Car: 3
        questions.addAll(questionsFor(car,
                new QSpec("Friction",
                        "Why do wheels roll instead of sliding like a crate?", "a",
                        "A rolling contact has far less friction than a sliding one, because each patch of the "
                                + "wheel only touches the ground briefly instead of dragging.",
                        "Rolling friction is much smaller than sliding friction", "Wheels are lighter",
                        "The ground is oily", "Gravity pushes harder on wheels"),
                new QSpec("Newton's law",
                        "For the same push, what happens to a heavier chassis?", "c",
                        "Force equals mass times acceleration: double the mass, half the acceleration.",
                        "It accelerates faster", "It stops forever",
                        "It accelerates more slowly", "Nothing changes"),
                new QSpec("Steering geometry",
                        "What is the trade-off of a solid axle joining both wheels?", "d",
                        "Both wheels must turn at the same speed, so in a turn one wheel scrubs. Simple and "
                                + "strong, but it fights smooth cornering.",
                        "Both wheels turn freely", "It weighs less",
                        "It always floats", "Both wheels share one speed, so they fight each other in turns")));

        // ---- Boombox: 3
        questions.addAll(questionsFor(boombox,
                new QSpec("Sound",
                        "What does the speaker cone actually do to make sound?", "b",
                        "It pushes the air back and forth. Those pressure waves travel through the air and you "
                                + "hear them as sound.",
                        "It glows", "It vibrates air to make pressure waves",
                        "It throws light", "It spins the air"),
                new QSpec("Energy",
                        "In a cassette deck, what energy change happens at the playback head?", "a",
                        "The magnetised tape pattern is read as a tiny electrical signal, which is amplified "
                                + "and turned into sound by the speaker.",
                        "Magnetic pattern to electrical signal", "Heat to light",
                        "Sound to magnet", "Electricity to gravity"),
                new QSpec("Waves",
                        "Why is antenna length matched to the radio station's wavelength?", "d",
                        "An antenna absorbs most strongly when its length is about a quarter of the wave's "
                                + "wavelength - so length tuning decides which station you get.",
                        "To look taller", "To store more energy",
                        "To shade the box", "Because it resonates best near a quarter-wavelength")));

        quizQuestionRepository.saveAll(questions);
        log.info("[SEED] questions={}", quizQuestionRepository.count());

        // ---------------------------------------------------------- assignments
        Assignment a1 = new Assignment();
        a1.setTitle("Flight Helmet: Identify the Safety Parts");
        a1.setInstructions("Open the Damaged Flight Helmet in AR mode. Tap each labelled part and explain out "
                + "loud how its design protects the pilot. Then screenshot the annotated model and note which "
                + "part you think absorbs the most impact energy.");
        a1.setDeadline(Instant.now().plus(14, ChronoUnit.DAYS));
        a1.setEduClass(eduClass);
        a1.setContent(helmet);
        a1.setTeacher(teacher);
        a1.setStatus(AssignmentStatus.NOT_STARTED);
        a1.setCreatedAt(now);

        Assignment a2 = new Assignment();
        a2.setTitle("Antique Camera: Complete the Optics Quiz");
        a2.setInstructions("Take the three-question Antique Camera quiz. You may retry as many times as you "
                + "like - the goal is 100%. Pay special attention to why the bellows controls focus.");
        a2.setDeadline(null);
        a2.setEduClass(eduClass);
        a2.setContent(camera);
        a2.setTeacher(teacher);
        a2.setStatus(AssignmentStatus.NOT_STARTED);
        a2.setCreatedAt(now);

        assignmentRepository.saveAll(List.of(a1, a2));
        log.info("[SEED] assignments={}", assignmentRepository.count());

        // ------------------------------------------------------ class schedules
        // Seed a realistic weekly timetable for the one demo class.
        // The time import at the top of the method (java.time.Instant) is already
        // present; we only need java.time.LocalTime which is used inline below.
        classScheduleRepository.saveAll(List.of(
                new ClassSchedule(eduClass, DayOfWeek.MONDAY,
                        java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0),
                        "Room 101", "Introduction to AR concepts"),
                new ClassSchedule(eduClass, DayOfWeek.MONDAY,
                        java.time.LocalTime.of(11, 0), java.time.LocalTime.of(12, 0),
                        "Lab A", "Hands-on AR session — bring your device"),
                new ClassSchedule(eduClass, DayOfWeek.WEDNESDAY,
                        java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0),
                        "Room 101", "Theory: Optics & Physics of AR"),
                new ClassSchedule(eduClass, DayOfWeek.WEDNESDAY,
                        java.time.LocalTime.of(14, 0), java.time.LocalTime.of(15, 0),
                        "Lab A", "Quiz review and model exploration"),
                new ClassSchedule(eduClass, DayOfWeek.FRIDAY,
                        java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 30),
                        "Room 205", "Weekly assessment & assignment hand-in")
        ));
        log.info("[SEED] schedules={}", classScheduleRepository.count());

        log.info("[SEED] ===== SEED COMPLETE: users={} classes={} classMembers={} contents={} parts={} "
                        + "questions={} assignments={} schedules={} =====",
                userRepository.count(), eduClassRepository.count(), classMemberRepository.count(),
                arContentRepository.count(), contentPartRepository.count(),
                quizQuestionRepository.count(), assignmentRepository.count(),
                classScheduleRepository.count());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * One quiz question definition. {@code options} must hold exactly 4 option texts in a/b/c/d
     * order; {@code correct} is the letter of the right one.
     */
    private record QSpec(String topic, String text, String correct, String explanation, String... options) {
    }

    /**
     * Converts {@link QSpec}s into {@link QuizQuestion}s, serialising options as the
     * pipe-separated blob {@code a|text|b|text|c|text|d|text}.
     */
    private List<QuizQuestion> questionsFor(ArContent content, QSpec... specs) {
        String[] keys = {"a", "b", "c", "d"};
        List<QuizQuestion> out = new ArrayList<>();
        int order = 1;
        for (QSpec spec : specs) {
            if (spec.options().length != 4) {
                throw new IllegalStateException("Quiz question must have exactly 4 options: " + spec.text());
            }
            StringBuilder blob = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) {
                    blob.append('|');
                }
                blob.append(keys[i]).append('|').append(spec.options()[i]);
            }
            QuizQuestion q = new QuizQuestion();
            q.setContent(content);
            q.setOrderIndex(order++);
            q.setTopic(spec.topic());
            q.setQuestionText(spec.text());
            q.setOptions(blob.toString());
            q.setCorrectOption(spec.correct().charAt(0));
            q.setExplanation(spec.explanation());
            out.add(q);
        }
        return out;
    }

    private User user(String name, String email, String plainPassword, Role role, String grade) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.hash(plainPassword));
        user.setRole(role);
        user.setGrade(grade);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private ArContent content(String title, Subject subject, String grade, String description,
                             String modelUrl, String markerId, User creator, Instant now) {
        ArContent content = new ArContent();
        content.setTitle(title);
        content.setSubject(subject);
        content.setGrade(grade);
        content.setDescription(description);
        content.setModelUrl(modelUrl);
        content.setMarkerId(markerId);
        content.setAudioUrl(null);
        content.setVideoUrl(null);
        content.setStatus(ContentStatus.PUBLISHED);
        content.setCreatedBy(creator);
        content.setVersion(1);
        content.setCreatedAt(now);
        content.setUpdatedAt(now);
        return content;
    }

    private ContentPart part(ArContent content, int orderIndex, String partName, String label,
                             String explanation) {
        ContentPart part = new ContentPart();
        part.setContent(content);
        part.setOrderIndex(orderIndex);
        part.setPartName(partName);
        part.setLabel(label);
        part.setExplanation(explanation);
        part.setAudioUrl(null);
        return part;
    }
}