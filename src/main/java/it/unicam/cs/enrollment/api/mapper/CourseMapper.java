package it.unicam.cs.enrollment.api.mapper;

import it.unicam.cs.enrollment.api.dto.request.CreateCourseRequest;
import it.unicam.cs.enrollment.api.dto.response.CourseResponse;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.service.command.CreateCourseCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Converts between {@link Course} entities, API DTOs and service commands.
 */
@ApplicationScoped
public class CourseMapper {

    /**
     * Injected because {@code enrollmentOpen} is computed against the current
     * instant, and a mapper that reads the system clock directly cannot be
     * tested at a chosen point in time. See {@code ClockProducer}.
     */
    private Clock clock;

    protected CourseMapper() {
        // required by CDI
    }

    @Inject
    public CourseMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateCourseCommand toCommand(CreateCourseRequest request) {
        return new CreateCourseCommand(
                request.getCode(),
                request.getTitle(),
                request.getDescription(),
                request.getCredits(),
                request.getCapacity(),
                request.getSemester(),
                request.getAcademicYear(),
                request.getProfessorId(),
                request.getEnrollmentOpensAt(),
                request.getEnrollmentClosesAt(),
                request.getPrerequisiteCourseIds());
    }

    /**
     * Summary view for listings.
     *
     * <p>{@code occupiedSeats} is passed IN rather than read from the entity.
     * The entity could compute it - {@code course.occupiedSeats()} exists - but
     * that walks the lazy enrollment collection and would run one query per
     * course in the page. The caller instead fetches all the counts in one
     * {@code GROUP BY} query and hands each one here.
     *
     * <p>A mapper that takes pre-computed values instead of reaching for them is
     * easier to reason about anyway: it becomes a pure function of its inputs.
     */
    public CourseResponse toSummaryResponse(Course course, long occupiedSeats) {
        CourseResponse response = new CourseResponse();

        response.setId(course.getId());
        response.setCode(course.getCode());
        response.setTitle(course.getTitle());
        response.setDescription(course.getDescription());
        response.setCredits(course.getCredits());
        response.setCapacity(course.getCapacity());
        response.setAvailableSeats(Math.max(0, course.getCapacity() - occupiedSeats));
        response.setSemester(course.getSemester().name());
        response.setAcademicYear(course.getAcademicYear());

        // Safe: every query that feeds this mapper JOIN FETCHes the professor.
        response.setProfessorId(course.getProfessor().getId());
        response.setProfessorName(course.getProfessor().fullName());

        response.setEnrollmentOpensAt(course.getEnrollmentOpensAt());
        response.setEnrollmentClosesAt(course.getEnrollmentClosesAt());
        response.setEnrollmentOpen(course.isEnrollmentOpen(clock.instant()));

        return response;
    }

    /**
     * Detail view: adds the prerequisite codes.
     *
     * <p><b>Precondition:</b> the course must have been loaded with
     * {@code CourseRepository.findByIdWithPrerequisites}. The summary view
     * deliberately omits this field, which is why the list endpoint can use the
     * cheaper query.
     */
    public CourseResponse toDetailResponse(Course course, long occupiedSeats) {
        CourseResponse response = toSummaryResponse(course, occupiedSeats);

        response.setPrerequisiteCodes(course.getPrerequisites().stream()
                .map(Course::getCode)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList()));

        return response;
    }
}
