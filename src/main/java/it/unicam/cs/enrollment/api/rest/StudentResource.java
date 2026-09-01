package it.unicam.cs.enrollment.api.rest;

import it.unicam.cs.enrollment.api.dto.PaginationParams;
import it.unicam.cs.enrollment.api.dto.request.CreateStudentRequest;
import it.unicam.cs.enrollment.api.dto.request.UpdateStudentRequest;
import it.unicam.cs.enrollment.api.dto.response.EnrollmentResponse;
import it.unicam.cs.enrollment.api.dto.response.PageResponse;
import it.unicam.cs.enrollment.api.dto.response.StudentResponse;
import it.unicam.cs.enrollment.api.mapper.EnrollmentMapper;
import it.unicam.cs.enrollment.api.mapper.StudentMapper;
import it.unicam.cs.enrollment.common.Page;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.domain.model.StudentStatus;
import it.unicam.cs.enrollment.exception.InvalidRequestException;
import it.unicam.cs.enrollment.service.EnrollmentService;
import it.unicam.cs.enrollment.service.StudentService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import java.util.List;
import java.util.Locale;

/**
 * REST endpoints for students.
 *
 * <h2>What a resource class is allowed to do</h2>
 * Three things, and nothing else:
 * <ol>
 *   <li>translate HTTP into a service call (DTO to command, path param to id);</li>
 *   <li>call exactly one service method;</li>
 *   <li>translate the result into an HTTP response (entity to DTO, choose the
 *       status code, set headers).</li>
 * </ol>
 * No business rules, no transactions, no repository access. If you find an
 * {@code if} here that decides something about the domain, it belongs in a
 * service. Keeping this discipline is what makes the same logic reusable from a
 * scheduled job or a message consumer later.
 *
 * <h2>The annotations</h2>
 * <ul>
 *   <li>{@code @Path} - the URI template this class serves, relative to
 *       {@code @ApplicationPath}.</li>
 *   <li>{@code @Produces}/{@code @Consumes} - content negotiation. Declaring
 *       them at class level applies them to every method.</li>
 *   <li>{@code @RequestScoped} - makes this a CDI bean with a per-request
 *       lifecycle, which is also the JAX-RS default for resource classes. Stating
 *       it explicitly documents the intent and enables CDI injection.</li>
 * </ul>
 */
@Path("/students")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudentResource {

    @Inject
    private StudentService studentService;

    @Inject
    private EnrollmentService enrollmentService;

    @Inject
    private StudentMapper studentMapper;

    @Inject
    private EnrollmentMapper enrollmentMapper;

    /**
     * {@code @Context} injects JAX-RS runtime objects. {@link UriInfo} knows the
     * URI of the current request, which is how we build a correct
     * {@code Location} header without hard-coding the host or the base path -
     * important the moment the application sits behind a reverse proxy.
     */
    @Context
    private UriInfo uriInfo;

    // ==================================================================
    // POST /api/students
    // ==================================================================

    /**
     * Registers a new student.
     *
     * <h3>Why 201 and not 200</h3>
     * {@code 201 Created} is the correct status when a request creates a new
     * resource, and it MUST be accompanied by a {@code Location} header pointing
     * at it. Clients (and generated SDKs) rely on this: it tells them the URI of
     * the thing they just made without having to guess how to build it.
     *
     * <h3>{@code @Valid} - where validation happens</h3>
     * The annotation makes JAX-RS run Bean Validation on the deserialised body
     * BEFORE this method executes. If a constraint fails, a
     * {@code ConstraintViolationException} is thrown and never reaches here; our
     * {@code ConstraintViolationExceptionMapper} turns it into a 400 listing
     * every offending field. Validation you cannot forget to call is worth more
     * than validation you have to remember.
     */
    @POST
    public Response create(@Valid @NotNull CreateStudentRequest request) {
        Student created = studentService.create(studentMapper.toCommand(request));

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.getId()))
                .build();

        return Response.created(location)
                .entity(studentMapper.toSummaryResponse(created))
                .build();
    }

    // ==================================================================
    // GET /api/students
    // ==================================================================

    /**
     * Searches students, paginated.
     *
     * <h3>Parsing the status parameter by hand</h3>
     * JAX-RS can convert a {@code @QueryParam} straight into an enum. We do not
     * let it, because when the value is invalid the specification says to return
     * <b>404 Not Found</b> - a genuinely confusing answer to
     * {@code ?status=BANANA}, since the collection exists perfectly well.
     *
     * <p>Taking the parameter as a String and converting it ourselves lets us
     * return a <b>400</b> that names the legal values. Knowing where a
     * framework's default behaviour is unhelpful, and quietly correcting it, is
     * a large part of building an API people enjoy using.
     *
     * <p>400, not 409: {@code BANANA} is not a status and never will be, so the
     * request is malformed rather than in conflict with the current state. See
     * {@link it.unicam.cs.enrollment.exception.InvalidRequestException}.
     */
    @GET
    public PageResponse<StudentResponse> search(
            @QueryParam("name") String nameFragment,
            @QueryParam("status") String status,
            @BeanParam PaginationParams pagination) {

        StudentStatus parsedStatus = parseStatus(status);

        Page<Student> page = studentService.search(
                nameFragment, parsedStatus, pagination.toPageRequest());

        // Page.map keeps every pagination field intact while converting the
        // payload - see Page#map for why that matters.
        return PageResponse.from(page.map(studentMapper::toSummaryResponse));
    }

    private StudentStatus parseStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return StudentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // The valid values are derived from the enum itself, so adding a
            // constant can never leave a hand-written list out of date.
            throw InvalidRequestException.invalidEnumValue("status", raw, StudentStatus.class);
        }
    }

    // ==================================================================
    // GET /api/students/{id}
    // ==================================================================

    /**
     * Returns one student with their full transcript.
     *
     * <p>The method returns the DTO directly rather than a {@link Response}.
     * Both are valid; the rule of thumb is:
     * <ul>
     *   <li>return the DTO when the status is always 200 - it is more readable
     *       and self-documenting;</li>
     *   <li>return {@code Response} when you need to choose a status or set
     *       headers, as {@link #create} does.</li>
     * </ul>
     * The 404 case does not need a {@code Response} either: the service throws,
     * and an exception mapper produces the status.
     */
    @GET
    @Path("/{id}")
    public StudentResponse findById(@PathParam("id") Long id) {
        return studentMapper.toDetailResponse(studentService.findByIdWithEnrollments(id));
    }

    /**
     * Lookup by matricola.
     *
     * <p>A sub-path rather than a query parameter on the collection, because
     * this identifies exactly one resource. {@code /students/by-number/100001}
     * reads as an address; {@code /students?studentNumber=100001} reads as a
     * filtered list that happens to hold one item - and would therefore be
     * expected to return an array.
     */
    @GET
    @Path("/by-number/{studentNumber}")
    public StudentResponse findByStudentNumber(@PathParam("studentNumber") String studentNumber) {
        return studentMapper.toSummaryResponse(studentService.findByStudentNumber(studentNumber));
    }

    // ==================================================================
    // GET /api/students/{id}/enrollments
    // ==================================================================

    /**
     * A SUB-RESOURCE: the enrollments belonging to one student.
     *
     * <p>Nesting the path expresses the ownership relationship, which is the
     * heart of REST resource design. Compare
     * {@code /students/42/enrollments} with {@code /enrollments?studentId=42}:
     * both work, but the first says "this collection belongs to student 42",
     * and that is the more honest description.
     */
    @GET
    @Path("/{id}/enrollments")
    public List<EnrollmentResponse> findEnrollments(@PathParam("id") Long id) {
        return enrollmentMapper.toResponseList(enrollmentService.findByStudent(id));
    }

    // ==================================================================
    // PATCH /api/students/{id}
    // ==================================================================

    /** Partial update. See {@link UpdateStudentRequest} for PATCH vs PUT. */
    @PATCH
    @Path("/{id}")
    public StudentResponse update(@PathParam("id") Long id,
                                  @Valid @NotNull UpdateStudentRequest request) {
        Student updated = studentService.update(
                id, request.getFirstName(), request.getLastName(), request.getEmail());
        return studentMapper.toSummaryResponse(updated);
    }

    // ==================================================================
    // State-changing actions
    // ==================================================================

    /**
     * Suspends a student.
     *
     * <h3>Actions that are not CRUD</h3>
     * Strict REST says "everything is a resource, manipulated with the standard
     * verbs", which would make this
     * {@code PATCH /students/42} with {@code {"status": "SUSPENDED"}}.
     *
     * <p>In practice most teams expose a sub-resource per action, as here.
     * The reasons are pragmatic and good: the intent is explicit and greppable,
     * each action can have its own permissions and audit entry, and the client
     * cannot construct an illegal transition by writing an arbitrary status. The
     * cost is a slightly less "pure" API, which nobody has ever regretted.
     */
    @POST
    @Path("/{id}/suspension")
    public StudentResponse suspend(@PathParam("id") Long id) {
        return studentMapper.toSummaryResponse(studentService.suspend(id));
    }

    @DELETE
    @Path("/{id}/suspension")
    public StudentResponse reinstate(@PathParam("id") Long id) {
        return studentMapper.toSummaryResponse(studentService.reinstate(id));
    }

    // ==================================================================
    // DELETE /api/students/{id}
    // ==================================================================

    /**
     * Deletes a student and, by cascade, their enrollments.
     *
     * <p>{@code 204 No Content} is the conventional answer to a successful
     * DELETE: the operation worked and there is nothing meaningful to return.
     * Returning 200 with an empty body, or with the deleted object, are both
     * things you will see in the wild - 204 is the one to prefer.
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        studentService.delete(id);
        return Response.noContent().build();
    }
}
