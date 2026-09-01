package it.unicam.cs.enrollment.service;

import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.common.PageRequest;
import it.unicam.cs.enrollment.common.logging.Loggable;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.Professor;
import it.unicam.cs.enrollment.domain.model.Semester;
import it.unicam.cs.enrollment.exception.BusinessRuleViolationException;
import it.unicam.cs.enrollment.exception.DuplicateResourceException;
import it.unicam.cs.enrollment.exception.ResourceNotFoundException;
import it.unicam.cs.enrollment.repository.CourseRepository;
import it.unicam.cs.enrollment.repository.EnrollmentRepository;
import it.unicam.cs.enrollment.repository.ProfessorRepository;
import it.unicam.cs.enrollment.service.command.CreateCourseCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.List;

/**
 * Use cases for the course catalogue.
 */
@Loggable
@ApplicationScoped
public class CourseService {

    private CourseRepository courseRepository;
    private ProfessorRepository professorRepository;
    private EnrollmentRepository enrollmentRepository;
    private Clock clock;
    private Logger log;

    protected CourseService() {
        // required by CDI
    }

    @Inject
    public CourseService(CourseRepository courseRepository,
                         ProfessorRepository professorRepository,
                         EnrollmentRepository enrollmentRepository,
                         Clock clock,
                         Logger log) {
        this.courseRepository = courseRepository;
        this.professorRepository = professorRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.clock = clock;
        this.log = log;
    }

    @Transactional
    public Course create(CreateCourseCommand command) {
        if (courseRepository.existsByCodeAndYear(command.getCode(), command.getAcademicYear())) {
            throw DuplicateResourceException.of(
                    "Course", "code/year",
                    command.getCode() + "/" + command.getAcademicYear());
        }

        // A rule that no single-field constraint could express: an interval must
        // not end before it starts. Validating it here, at the point of
        // creation, means no invalid course can ever reach the database.
        if (!command.getEnrollmentClosesAt().isAfter(command.getEnrollmentOpensAt())) {
            throw new BusinessRuleViolationException(
                    "INVALID_ENROLLMENT_WINDOW",
                    "The enrollment window must close after it opens");
        }

        Professor professor = professorRepository.findById(command.getProfessorId())
                .orElseThrow(() -> ResourceNotFoundException.of("Professor", command.getProfessorId()));

        Course course = new Course(
                command.getCode(),
                command.getTitle(),
                command.getCredits(),
                command.getCapacity(),
                command.getSemester(),
                command.getAcademicYear(),
                professor,
                command.getEnrollmentOpensAt(),
                command.getEnrollmentClosesAt());
        course.setDescription(command.getDescription());

        for (Long prerequisiteId : command.getPrerequisiteCourseIds()) {
            Course prerequisite = courseRepository.findById(prerequisiteId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Course", prerequisiteId));
            course.addPrerequisite(prerequisite);
        }

        courseRepository.save(course);
        courseRepository.flush();

        log.info("Created course {} taught by {}", course.displayCode(), professor.fullName());
        return course;
    }

    @Transactional
    public Course findById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    @Transactional
    public Course findByIdWithPrerequisites(Long id) {
        return courseRepository.findByIdWithPrerequisites(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    @Transactional
    public Page<Course> findByYearAndSemester(int academicYear, Semester semester, PageRequest pageRequest) {
        return courseRepository.findByYearAndSemester(academicYear, semester, pageRequest);
    }

    /** Courses a student can currently sign up for. */
    @Transactional
    public List<Course> findOpenForEnrollment() {
        return courseRepository.findOpenForEnrollment(clock.instant());
    }

    /**
     * How many seats are left. Answered with a {@code COUNT} query rather than
     * {@code course.availableSeats()}, which would load every enrollment row into
     * memory to compute the same number. See {@code Course.occupiedSeats()} for
     * the discussion.
     */
    @Transactional
    public long availableSeats(Long courseId) {
        Course course = findById(courseId);
        long occupied = enrollmentRepository.countOccupiedSeats(courseId);
        return Math.max(0, course.getCapacity() - occupied);
    }

    /**
     * Occupied-seat counts for many courses at once.
     *
     * <p>Exists so that a course LISTING can show seat availability without
     * issuing one count query per row. See
     * {@code EnrollmentRepository.countOccupiedSeatsByCourse} for the reasoning.
     *
     * @return course id to occupied seats; ids with no enrollments are absent,
     *         so callers should use {@code getOrDefault(id, 0L)}
     */
    @Transactional
    public java.util.Map<Long, Long> occupiedSeatsFor(java.util.Collection<Long> courseIds) {
        return enrollmentRepository.countOccupiedSeatsByCourse(courseIds);
    }

    /**
     * Adds a prerequisite, refusing to create a cycle.
     *
     * <p>Only DIRECT self-reference is checked here. A full cycle check
     * (A requires B, B requires C, C requires A) needs a graph traversal, and
     * flagging that limitation openly is better than pretending the guard is
     * complete. Comments that admit what the code does not do are more valuable
     * than comments that restate what it does.
     */
    @Transactional
    public Course addPrerequisite(Long courseId, Long prerequisiteId) {
        if (courseId.equals(prerequisiteId)) {
            throw new BusinessRuleViolationException(
                    "CIRCULAR_PREREQUISITE", "A course cannot be its own prerequisite");
        }
        // The detail query, not the plain findById: the REST layer maps the
        // returned course to a detail response, which reads both the professor
        // and the prerequisite collection AFTER this transaction has closed.
        Course course = findByIdWithPrerequisites(courseId);
        Course prerequisite = findById(prerequisiteId);
        course.addPrerequisite(prerequisite);

        log.info("Course {} now requires {}", course.getCode(), prerequisite.getCode());
        return course;
    }

    /**
     * Changes course capacity, refusing to shrink it below the number of
     * students already enrolled.
     *
     * <p>A good example of a rule that only exists because of EXISTING STATE.
     * You cannot express it as a field constraint; it needs a query.
     */
    @Transactional
    public Course changeCapacity(Long courseId, int newCapacity) {
        // Same reason as addPrerequisite: the response mapping needs the
        // professor, and it happens after this transaction commits.
        Course course = findByIdWithPrerequisites(courseId);
        long occupied = enrollmentRepository.countOccupiedSeats(courseId);

        if (newCapacity < occupied) {
            throw new BusinessRuleViolationException(
                    "CAPACITY_BELOW_ENROLLED",
                    "Cannot reduce capacity to " + newCapacity
                            + ": " + occupied + " students are already enrolled");
        }
        course.setCapacity(newCapacity);
        log.info("Course {} capacity changed to {}", course.getCode(), newCapacity);
        return course;
    }
}
