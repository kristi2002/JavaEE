package it.unicam.cs.enrollment.service;

import it.unicam.cs.enrollment.common.logging.Loggable;
import it.unicam.cs.enrollment.domain.event.EnrollmentCreatedEvent;
import it.unicam.cs.enrollment.domain.event.GradeRecordedEvent;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Enrollment;
import it.unicam.cs.enrollment.domain.model.EnrollmentStatus;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.exception.BusinessRuleViolationException;
import it.unicam.cs.enrollment.exception.DuplicateResourceException;
import it.unicam.cs.enrollment.exception.ResourceNotFoundException;
import it.unicam.cs.enrollment.repository.CourseRepository;
import it.unicam.cs.enrollment.repository.EnrollmentRepository;
import it.unicam.cs.enrollment.repository.StudentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The application's core USE CASES for enrollments.
 *
 * <h2>What belongs in a service layer</h2>
 * The service is the ORCHESTRATOR. It owns:
 * <ul>
 *   <li>the TRANSACTION BOUNDARY - one business operation, one transaction;</li>
 *   <li>rules that span several entities ("does this student meet the
 *       prerequisites for that course?") - a rule about one entity alone belongs
 *       ON that entity, which is why the state machine lives in
 *       {@link EnrollmentStatus} and not here;</li>
 *   <li>translating domain-level failures into the application's exception
 *       vocabulary;</li>
 *   <li>publishing domain events.</li>
 * </ul>
 * It must NOT know about HTTP. No {@code Response}, no status codes, no
 * {@code HttpServletRequest}. That separation is what lets the same service be
 * driven by a REST endpoint, a scheduled job, a message consumer or a test.
 *
 * <h2>Constructor injection</h2>
 * Dependencies arrive through the constructor rather than being set on fields.
 * This is worth insisting on:
 * <ul>
 *   <li>the object is fully formed the moment it exists - no half-initialised
 *       state;</li>
 *   <li>a unit test just calls {@code new EnrollmentService(mockA, mockB, ...)}
 *       with no container and no reflection;</li>
 *   <li>a constructor with eight parameters is visibly painful, which is useful
 *       feedback that the class is doing too much. Field injection hides that
 *       smell.</li>
 * </ul>
 * The {@code protected} no-argument constructor exists purely so the container
 * can build its client proxy for this normal-scoped bean.
 */
@Loggable
@ApplicationScoped
public class EnrollmentService {

    private StudentRepository studentRepository;
    private CourseRepository courseRepository;
    private EnrollmentRepository enrollmentRepository;

    /**
     * {@code Event<T>} is the CDI event publisher. Injecting it, rather than
     * calling observers directly, is what keeps this service unaware of who
     * listens.
     */
    private Event<EnrollmentCreatedEvent> enrollmentCreatedEvent;
    private Event<GradeRecordedEvent> gradeRecordedEvent;

    /** Injected so that every time-dependent rule below is testable. */
    private Clock clock;

    /** Supplied by {@code LoggerProducer}, already named after this class. */
    private Logger log;

    /** Required by CDI for proxying. Never call it yourself. */
    protected EnrollmentService() {
        // required by CDI
    }

    @Inject
    public EnrollmentService(StudentRepository studentRepository,
                             CourseRepository courseRepository,
                             EnrollmentRepository enrollmentRepository,
                             Event<EnrollmentCreatedEvent> enrollmentCreatedEvent,
                             Event<GradeRecordedEvent> gradeRecordedEvent,
                             Clock clock,
                             Logger log) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentCreatedEvent = enrollmentCreatedEvent;
        this.gradeRecordedEvent = gradeRecordedEvent;
        this.clock = clock;
        this.log = log;
    }

    // ==================================================================
    // USE CASE: enrol a student in a course
    // ==================================================================

    /**
     * Enrols a student, enforcing every business rule in order.
     *
     * <h3>{@code @Transactional} - the single most important annotation here</h3>
     * The container starts a JTA transaction before the method body and commits
     * it after. If a RuntimeException escapes, it rolls back instead - so a
     * failure halfway through cannot leave a half-written enrollment behind.
     * That all-or-nothing property is ATOMICITY, the A in ACID.
     *
     * <p>{@code TxType.REQUIRED} is the default and the right one 95% of the
     * time: join the caller's transaction if there is one, otherwise start a new
     * one. Worth knowing the others:
     * <ul>
     *   <li>{@code REQUIRES_NEW} - always a fresh, independent transaction. Use
     *       when work must survive the caller rolling back, e.g. an audit
     *       record.</li>
     *   <li>{@code MANDATORY} - throws unless a transaction is already running.
     *       A way to state "I am not a transaction boundary".</li>
     *   <li>{@code SUPPORTS} / {@code NOT_SUPPORTED} / {@code NEVER} - rarer.</li>
     * </ul>
     *
     * <h3>Why the ordering of the checks matters</h3>
     * Student eligibility is checked first because it is cheap and needs no
     * lock: there is no point serialising every request on the course row just
     * to discover the student was suspended.
     *
     * <p>The row lock is taken next - before the window, duplicate, capacity and
     * prerequisite checks - because the {@code Course} those checks read is the
     * very row that has to be locked. Loading it unlocked and locking it later
     * would read the row twice and act on the first, stale copy in between.
     *
     * <p>The cost is that four checks run while the lock is held, lengthening
     * the window in which other enrollments for this course queue behind us. The
     * benefit is that the capacity check is trustworthy. Which checks belong
     * inside a lock and which belong outside it is a judgement call, not a rule.
     *
     * @return the newly created, persisted enrollment
     * @throws ResourceNotFoundException      student or course does not exist
     * @throws DuplicateResourceException     already enrolled
     * @throws BusinessRuleViolationException any domain rule refused
     */
    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        Instant now = clock.instant();

        // --- 1. The student must exist and be eligible ---------------------
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        if (!student.canEnroll()) {
            throw BusinessRuleViolationException.studentNotEligible(
                    student.getStudentNumber(), student.getStatus().name());
        }

        // --- 2. Lock the course row ----------------------------------------
        // SELECT ... FOR UPDATE. From here until commit, no other transaction
        // can enrol anyone in this course, which is what makes the capacity
        // check below trustworthy. See CourseRepository for the race it prevents.
        Course course = courseRepository.findByIdWithPessimisticLock(courseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseId));

        // --- 3. The enrollment window must be open -------------------------
        if (!course.isEnrollmentOpen(now)) {
            throw BusinessRuleViolationException.enrollmentWindowClosed(course.getCode());
        }

        // --- 4. No double enrollment ---------------------------------------
        // A friendly early check. The UNIQUE constraint on
        // (student_id, course_id) is the actual guarantee.
        enrollmentRepository.findByStudentAndCourse(studentId, courseId)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Student " + student.getStudentNumber()
                                    + " is already enrolled in course " + course.getCode()
                                    + " (status: " + existing.getStatus() + ")");
                });

        // --- 5. Capacity ---------------------------------------------------
        long occupied = enrollmentRepository.countOccupiedSeats(courseId);
        if (occupied >= course.getCapacity()) {
            throw BusinessRuleViolationException.courseFull(course.getCode(), course.getCapacity());
        }

        // --- 6. Prerequisites ----------------------------------------------
        verifyPrerequisites(student, course);

        // --- 7. Create and persist -----------------------------------------
        Enrollment enrollment = Enrollment.create(student, course, now);
        enrollmentRepository.save(enrollment);

        // flush() sends the INSERT now rather than at commit. That matters here:
        // if the unique constraint fires we want the failure INSIDE this method,
        // where the stack trace still points at the enrollment logic, not later
        // during commit where it surfaces from deep inside the container.
        enrollmentRepository.flush();

        log.info("Student {} enrolled in course {} ({} of {} seats now taken)",
                student.getStudentNumber(), course.getCode(), occupied + 1, course.getCapacity());

        // --- 8. Announce what happened --------------------------------------
        // fire() is synchronous: observers run on this thread, inside this
        // transaction. If an observer throws, the enrollment rolls back too.
        // That is sometimes exactly right (an audit record must not be lost) and
        // sometimes wrong (a failing email should not undo an enrollment).
        // See EnrollmentNotificationListener for how transactional observers let
        // you choose.
        enrollmentCreatedEvent.fire(new EnrollmentCreatedEvent(
                enrollment.getId(),
                student.getId(),
                student.getStudentNumber(),
                student.getEmail() != null ? student.getEmail().getValue() : null,
                course.getId(),
                course.getCode(),
                course.getTitle(),
                now));

        return enrollment;
    }

    /**
     * Every prerequisite course code must have been PASSED by this student.
     *
     * <p>Note the cost: one query per prerequisite. With the two or three
     * prerequisites a real course has, that is fine and the code stays readable.
     * If courses had fifty, you would rewrite it as a single query with
     * {@code WHERE c.code IN :codes} and compare the returned set. Knowing when
     * a loop of queries is acceptable, and when it is the N+1 problem in
     * disguise, is a judgement call worth practising.
     *
     * <p>{@code course.getPrerequisites()} triggers a lazy load here. We are
     * inside the transaction, so it works. It could not be JOIN FETCHed in step
     * 2 above, because combining a fetch join with {@code FOR UPDATE} makes the
     * lock cover the joined rows as well - and several databases simply reject
     * the combination.
     */
    private void verifyPrerequisites(Student student, Course course) {
        List<String> missing = course.getPrerequisites().stream()
                .map(Course::getCode)
                .filter(code -> !enrollmentRepository.hasCompletedCourseCode(student.getId(), code))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw BusinessRuleViolationException.prerequisitesNotMet(
                    course.getCode(), String.join(", ", missing));
        }
    }

    // ==================================================================
    // USE CASE: register an exam result
    // ==================================================================

    /**
     * Records a passing grade (18-30, optionally with honours).
     *
     * <h3>Exception translation between layers</h3>
     * The entity throws plain {@code IllegalArgumentException} /
     * {@code IllegalStateException}, because the domain model must not depend on
     * application-specific types. The service catches those and rethrows them as
     * {@link BusinessRuleViolationException}, which the REST layer knows how to
     * turn into a 409.
     *
     * <p>Each layer speaking its own vocabulary, with an explicit translation at
     * the boundary, is what keeps the layers independently reusable. Always pass
     * the original exception as the {@code cause} so the stack trace survives.
     */
    @Transactional
    public Enrollment recordPass(Long enrollmentId, int grade, boolean withHonours) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        Instant now = clock.instant();

        try {
            enrollment.recordPass(grade, withHonours, now);
        } catch (IllegalArgumentException e) {
            throw BusinessRuleViolationException.invalidGrade(e.getMessage());
        } catch (IllegalStateException e) {
            throw BusinessRuleViolationException.illegalStateTransition(e.getMessage());
        }

        log.info("Recorded grade {} for enrollment {} (student {}, course {})",
                enrollment.formattedGrade(), enrollmentId,
                enrollment.getStudent().getStudentNumber(), enrollment.getCourse().getCode());

        gradeRecordedEvent.fire(new GradeRecordedEvent(
                enrollment.getId(),
                enrollment.getStudent().getStudentNumber(),
                enrollment.getCourse().getCode(),
                enrollment.getGrade(),
                enrollment.isWithHonours(),
                true,
                now));

        // NOTE: there is no repository.save() call here, and that is not an
        // oversight. `enrollment` is a MANAGED entity: it belongs to the current
        // persistence context, so JPA compares it against its loaded snapshot at
        // commit time and writes an UPDATE for whatever changed. This is called
        // DIRTY CHECKING, and it is the mechanism that surprises newcomers most.
        return enrollment;
    }

    /** Records a failed exam. The enrollment stays, so the student can retake it. */
    @Transactional
    public Enrollment recordFailure(Long enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        Instant now = clock.instant();

        try {
            enrollment.recordFailure(now);
        } catch (IllegalStateException e) {
            throw BusinessRuleViolationException.illegalStateTransition(e.getMessage());
        }

        log.info("Recorded exam failure for enrollment {}", enrollmentId);

        gradeRecordedEvent.fire(new GradeRecordedEvent(
                enrollment.getId(),
                enrollment.getStudent().getStudentNumber(),
                enrollment.getCourse().getCode(),
                null, false, false, now));

        return enrollment;
    }

    // ==================================================================
    // USE CASE: withdraw / retake
    // ==================================================================

    @Transactional
    public Enrollment withdraw(Long enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        try {
            enrollment.withdraw(clock.instant());
        } catch (IllegalStateException e) {
            throw BusinessRuleViolationException.illegalStateTransition(e.getMessage());
        }
        log.info("Enrollment {} withdrawn; a seat was released on course {}",
                enrollmentId, enrollment.getCourse().getCode());
        return enrollment;
    }

    /**
     * Re-activates a FAILED enrollment so the student can sit the exam again.
     *
     * <p>No capacity check: the student already holds the seat (see
     * {@link EnrollmentStatus#occupiesSeat()}), so re-activating cannot push the
     * course over capacity. Being able to justify the ABSENCE of a check is as
     * important as the checks themselves.
     */
    @Transactional
    public Enrollment retake(Long enrollmentId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        try {
            enrollment.retake();
        } catch (IllegalStateException e) {
            throw BusinessRuleViolationException.illegalStateTransition(e.getMessage());
        }
        log.info("Enrollment {} re-activated for a retake", enrollmentId);
        return enrollment;
    }

    // ==================================================================
    // Queries
    // ==================================================================

    /**
     * {@code @Transactional} on a read as well.
     *
     * <p>Reads need a transaction too: it is what gives the persistence context
     * a defined lifetime, and what stops lazy loading from throwing
     * {@code LazyInitializationException} halfway through. It also means a
     * multi-statement read sees one consistent snapshot rather than data that
     * shifts under it.
     */
    @Transactional
    public List<Enrollment> findByStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw ResourceNotFoundException.of("Student", studentId);
        }
        return enrollmentRepository.findByStudentWithCourse(studentId);
    }

    @Transactional
    public List<Enrollment> findByCourse(Long courseId, EnrollmentStatus status) {
        return enrollmentRepository.findByCourseAndStatus(
                courseId, status != null ? status : EnrollmentStatus.ACTIVE);
    }

    @Transactional
    public Enrollment findById(Long enrollmentId) {
        return requireEnrollment(enrollmentId);
    }

    /**
     * A tiny private helper used by every method above.
     *
     * <p>Extracting "load it or throw 404" removes six identical
     * {@code orElseThrow} lines. Repetition like that is not just verbose - it is
     * where inconsistencies breed, because one of the six eventually gets a
     * slightly different message or forgets to throw at all.
     */
    private Enrollment requireEnrollment(Long enrollmentId) {
        // findByIdWithDetails, not findById: the REST layer maps this entity to
        // a DTO after the transaction has closed, so student and course must
        // already be loaded. See EnrollmentRepository.findByIdWithDetails.
        return enrollmentRepository.findByIdWithDetails(enrollmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Enrollment", enrollmentId));
    }
}
