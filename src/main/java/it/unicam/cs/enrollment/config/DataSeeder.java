package it.unicam.cs.enrollment.config;

import it.unicam.cs.enrollment.domain.model.AcademicTitle;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Email;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.domain.model.Semester;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.repository.CourseRepository;
import it.unicam.cs.enrollment.repository.ProfessorRepository;
import it.unicam.cs.enrollment.repository.StudentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Populates the database with demonstration data on first startup.
 *
 * <h2>Idempotence</h2>
 * The method checks whether data already exists and returns early if it does. It
 * is therefore safe to run on every deploy, which matters because WildFly
 * redeploys the application whenever the WAR changes.
 *
 * <p>"Safe to run twice" is a property worth designing into anything that starts
 * automatically - startup hooks, migrations, retries, message consumers. The
 * word for it is IDEMPOTENT, and it comes up constantly once you start
 * distributing work across machines that can each fail and be restarted.
 *
 * <h2>This is demo data, not a migration</h2>
 * Seeding from code is fine for reference data and for a learning project. For
 * real schema and data changes you want FLYWAY or LIQUIBASE: numbered SQL files
 * checked into git, applied in order, recorded in a table so each runs exactly
 * once. That gives you review, history and a repeatable path from an empty
 * database to production.
 */
@ApplicationScoped
public class DataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DataSeeder.class);

    @Inject
    private StudentRepository studentRepository;

    @Inject
    private ProfessorRepository professorRepository;

    @Inject
    private CourseRepository courseRepository;

    @Inject
    private Clock clock;

    /**
     * {@code @Transactional} on a CDI bean, invoked from the startup EJB.
     *
     * <p>Note it is called from {@link ApplicationBootstrap} rather than being a
     * {@code @PostConstruct} here. Transaction interceptors do not apply to
     * {@code @PostConstruct} on a normal-scoped CDI bean, so a
     * {@code @Transactional @PostConstruct} would run with NO transaction and
     * fail. Crossing a bean boundary is what makes the interceptor fire.
     *
     * <p>This is the same reason a {@code @Transactional} method calling another
     * {@code @Transactional} method ON THE SAME OBJECT does not start a new
     * transaction: the call never leaves the instance, so no proxy is involved.
     * It is one of the most common Jakarta EE / Spring gotchas.
     */
    @Transactional
    public void seedIfEmpty() {
        if (studentRepository.count() > 0 || courseRepository.count() > 0) {
            LOG.info("Database already contains data - skipping seed");
            return;
        }

        LOG.info("Empty database detected - inserting demonstration data");

        // ---------------------------------------------------------------
        // Professors
        // ---------------------------------------------------------------
        Professor bianchi = professorRepository.save(new Professor(
                "P001", "Elena", "Bianchi", Email.of("elena.bianchi@unicam.it"),
                AcademicTitle.FULL_PROFESSOR, "Computer Science"));

        Professor rossi = professorRepository.save(new Professor(
                "P002", "Marco", "Rossi", Email.of("marco.rossi@unicam.it"),
                AcademicTitle.ASSOCIATE_PROFESSOR, "Computer Science"));

        Professor conti = professorRepository.save(new Professor(
                "P003", "Giulia", "Conti", Email.of("giulia.conti@unicam.it"),
                AcademicTitle.ASSISTANT_PROFESSOR, "Mathematics"));

        // ---------------------------------------------------------------
        // Courses
        //
        // The enrollment window is anchored to the CURRENT time so the demo
        // works whenever you run it. Hard-coded dates in seed data go stale and
        // then every example in the README stops working.
        // ---------------------------------------------------------------
        Instant now = clock.instant();
        Instant opened = now.minus(7, ChronoUnit.DAYS);
        Instant closes = now.plus(30, ChronoUnit.DAYS);
        int academicYear = 2025;

        Course programming1 = courseRepository.save(new Course(
                "CS101", "Programming Fundamentals", 12, 120,
                Semester.FALL, academicYear, bianchi, opened, closes));
        programming1.setDescription(
                "Imperative and object-oriented programming in Java. Variables, control "
                        + "flow, methods, classes, collections and an introduction to testing.");

        Course algorithms = courseRepository.save(new Course(
                "CS201", "Algorithms and Data Structures", 9, 80,
                Semester.SPRING, academicYear, rossi, opened, closes));
        algorithms.setDescription(
                "Complexity analysis, sorting and searching, trees, graphs, "
                        + "dynamic programming.");
        // A prerequisite chain, so the rule has something to enforce.
        algorithms.addPrerequisite(programming1);

        Course databases = courseRepository.save(new Course(
                "CS301", "Database Systems", 9, 60,
                Semester.FALL, academicYear, rossi, opened, closes));
        databases.setDescription(
                "The relational model, SQL, normalisation, transactions, indexing "
                        + "and query optimisation.");
        databases.addPrerequisite(programming1);

        Course enterprise = courseRepository.save(new Course(
                "CS401", "Enterprise Software Architecture", 6, 3,
                Semester.SPRING, academicYear, bianchi, opened, closes));
        enterprise.setDescription(
                "Layered architectures, persistence, transactions and REST APIs with "
                        + "Jakarta EE. Deliberately given a capacity of 3 so the "
                        + "COURSE_FULL rule is easy to trigger from the API.");
        enterprise.addPrerequisite(programming1);
        enterprise.addPrerequisite(databases);

        Course calculus = courseRepository.save(new Course(
                "MA101", "Calculus I", 12, 200,
                Semester.FALL, academicYear, conti, opened, closes));
        calculus.setDescription("Limits, derivatives, integrals and series.");

        // A course whose window has already CLOSED, so the
        // ENROLLMENT_WINDOW_CLOSED rule can be demonstrated.
        Course closedCourse = courseRepository.save(new Course(
                "CS150", "Computer Architecture", 6, 90,
                Semester.FALL, academicYear - 1, conti,
                now.minus(400, ChronoUnit.DAYS),
                now.minus(370, ChronoUnit.DAYS)));
        closedCourse.setDescription(
                "Last year's course - its enrollment window is closed, which makes it "
                        + "useful for testing the window rule.");

        // ---------------------------------------------------------------
        // Students
        // ---------------------------------------------------------------
        studentRepository.save(new Student("100001", "Luca", "Ferrari",
                Email.of("luca.ferrari@studenti.unicam.it"),
                LocalDate.of(2004, 3, 14), 2023));

        studentRepository.save(new Student("100002", "Sofia", "Greco",
                Email.of("sofia.greco@studenti.unicam.it"),
                LocalDate.of(2003, 11, 2), 2022));

        studentRepository.save(new Student("100003", "Matteo", "Esposito",
                Email.of("matteo.esposito@studenti.unicam.it"),
                LocalDate.of(2005, 7, 21), 2024));

        Student suspended = studentRepository.save(new Student("100004", "Chiara", "Romano",
                Email.of("chiara.romano@studenti.unicam.it"),
                LocalDate.of(2004, 1, 9), 2023));
        // Suspended, so the STUDENT_NOT_ELIGIBLE rule can be demonstrated.
        suspended.suspend();

        LOG.info("Seed complete: {} professors, {} courses, {} students",
                professorRepository.count(), courseRepository.count(), studentRepository.count());
    }
}
