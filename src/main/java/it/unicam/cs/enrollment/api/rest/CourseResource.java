package it.unicam.cs.enrollment.api.rest;

import it.unicam.cs.enrollment.api.dto.PaginationParams;
import it.unicam.cs.enrollment.api.dto.request.CreateCourseRequest;
import it.unicam.cs.enrollment.api.dto.response.CourseResponse;
import it.unicam.cs.enrollment.api.dto.response.EnrollmentResponse;
import it.unicam.cs.enrollment.api.dto.response.PageResponse;
import it.unicam.cs.enrollment.api.mapper.CourseMapper;
import it.unicam.cs.enrollment.api.mapper.EnrollmentMapper;
import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.domain.model.Course;
import it.unicam.cs.enrollment.domain.model.EnrollmentStatus;
import it.unicam.cs.enrollment.domain.model.Semester;
import it.unicam.cs.enrollment.exception.InvalidRequestException;
import it.unicam.cs.enrollment.service.CourseService;
import it.unicam.cs.enrollment.service.EnrollmentService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoints for the course catalogue.
 */
@Path("/courses")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CourseResource {

    @Inject
    private CourseService courseService;

    @Inject
    private EnrollmentService enrollmentService;

    @Inject
    private CourseMapper courseMapper;

    @Inject
    private EnrollmentMapper enrollmentMapper;

    @Context
    private UriInfo uriInfo;

    @POST
    public Response create(@Valid @NotNull CreateCourseRequest request) {
        Course created = courseService.create(courseMapper.toCommand(request));

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.getId()))
                .build();

        return Response.created(location)
                .entity(courseMapper.toSummaryResponse(created, 0L))
                .build();
    }

    /**
     * Browses the catalogue for one academic year.
     *
     * <h3>Avoiding N+1 in the mapping layer</h3>
     * Each course in the response carries {@code availableSeats}. The obvious
     * implementation asks the database per course - 20 rows, 20 count queries.
     *
     * <p>Instead we collect the ids, fetch every count in ONE {@code GROUP BY}
     * query, and feed each mapper call from the resulting map. This is the
     * standard shape for the problem, and worth recognising: whenever you are
     * about to call something per item in a list, ask whether the whole list can
     * be answered at once.
     */
    @GET
    public PageResponse<CourseResponse> list(
            @QueryParam("year") @DefaultValue("2025") @Min(2000) int academicYear,
            @QueryParam("semester") String semester,
            @BeanParam PaginationParams pagination) {

        Semester parsedSemester = parseSemester(semester);

        Page<Course> page = courseService.findByYearAndSemester(
                academicYear, parsedSemester, pagination.toPageRequest());

        Map<Long, Long> occupied = courseService.occupiedSeatsFor(
                page.getContent().stream().map(Course::getId).collect(Collectors.toList()));

        return PageResponse.from(page.map(course ->
                courseMapper.toSummaryResponse(course, occupied.getOrDefault(course.getId(), 0L))));
    }

    /**
     * Courses currently open for enrollment.
     *
     * <p>Not paginated, deliberately: the number of courses open at any moment
     * is bounded by the size of the catalogue, in the dozens. Pagination has a
     * cost in client complexity, and applying it where the result set cannot
     * grow without bound is ceremony. Paginate what can grow with your DATA
     * (students, enrollments), not what is fixed by your DOMAIN.
     */
    @GET
    @Path("/open")
    public List<CourseResponse> findOpen() {
        List<Course> courses = courseService.findOpenForEnrollment();

        Map<Long, Long> occupied = courseService.occupiedSeatsFor(
                courses.stream().map(Course::getId).collect(Collectors.toList()));

        return courses.stream()
                .map(course -> courseMapper.toSummaryResponse(
                        course, occupied.getOrDefault(course.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /** One course, including its prerequisite codes. */
    @GET
    @Path("/{id}")
    public CourseResponse findById(@PathParam("id") Long id) {
        Course course = courseService.findByIdWithPrerequisites(id);
        return courseMapper.toDetailResponse(course, occupiedSeats(id));
    }

    /**
     * Occupied seats for a single course, reusing the batch query.
     *
     * <p>The earlier version computed this as
     * {@code course.getCapacity() - courseService.availableSeats(id)}, which
     * re-loaded the whole course just to subtract two numbers back into the one
     * we already had. Going through the same batch method the list endpoints use
     * is one query instead of two, and means there is a single definition of
     * "occupied" in the codebase.
     */
    private long occupiedSeats(Long courseId) {
        return courseService.occupiedSeatsFor(Collections.singletonList(courseId))
                .getOrDefault(courseId, 0L);
    }

    /** The roster: who is enrolled on this course. */
    @GET
    @Path("/{id}/enrollments")
    public List<EnrollmentResponse> roster(@PathParam("id") Long id,
                                           @QueryParam("status") String status) {
        EnrollmentStatus parsedStatus = parseEnrollmentStatus(status);
        return enrollmentMapper.toResponseList(enrollmentService.findByCourse(id, parsedStatus));
    }

    /**
     * Adds a prerequisite.
     *
     * <p>Modelled as PUT on a nested resource: "the relationship between course
     * A and prerequisite B exists". PUT is IDEMPOTENT - applying it twice leaves
     * the same state - which matches set semantics exactly, and matters because
     * a client that retries after a timeout must not corrupt anything.
     */
    @jakarta.ws.rs.PUT
    @Path("/{id}/prerequisites/{prerequisiteId}")
    public CourseResponse addPrerequisite(@PathParam("id") Long id,
                                          @PathParam("prerequisiteId") Long prerequisiteId) {
        Course course = courseService.addPrerequisite(id, prerequisiteId);
        return courseMapper.toDetailResponse(course, occupiedSeats(id));
    }

    /**
     * Changes capacity; refuses to go below the number already enrolled.
     *
     * <p>{@code @Min(1)} on the parameter means {@code ?value=0} is rejected as a
     * 400 before the service runs — a course with zero seats is not a meaningful
     * request, so it is malformed input rather than a business conflict. The
     * service's {@code CAPACITY_BELOW_ENROLLED} rule (409) covers the different
     * case of a valid capacity that is smaller than the number of students
     * already holding a seat.
     */
    @PATCH
    @Path("/{id}/capacity")
    public CourseResponse changeCapacity(@PathParam("id") Long id,
                                         @QueryParam("value") @NotNull @Min(1) Integer newCapacity) {
        Course course = courseService.changeCapacity(id, newCapacity);
        return courseMapper.toSummaryResponse(course, occupiedSeats(id));
    }

    // ------------------------------------------------------------------
    // Parameter parsing (see StudentResource.parseStatus for the rationale)
    // ------------------------------------------------------------------

    private Semester parseSemester(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Semester.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw InvalidRequestException.invalidEnumValue("semester", raw, Semester.class);
        }
    }

    private EnrollmentStatus parseEnrollmentStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return EnrollmentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw InvalidRequestException.invalidEnumValue("status", raw, EnrollmentStatus.class);
        }
    }
}
