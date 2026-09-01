package it.unicam.cs.enrollment.api.rest;

import it.unicam.cs.enrollment.api.dto.request.EnrollRequest;
import it.unicam.cs.enrollment.api.dto.request.RecordGradeRequest;
import it.unicam.cs.enrollment.api.dto.response.EnrollmentResponse;
import it.unicam.cs.enrollment.api.mapper.EnrollmentMapper;
import it.unicam.cs.enrollment.domain.model.Enrollment;
import it.unicam.cs.enrollment.service.EnrollmentService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

/**
 * REST endpoints for enrollments - the most interesting resource in the API,
 * because almost every operation on it can legitimately be refused.
 *
 * <h2>The status codes this resource can return, and what each means</h2>
 * <table border="1">
 *   <caption>Status codes</caption>
 *   <tr><th>Code</th><th>When</th><th>Produced by</th></tr>
 *   <tr><td>201</td><td>enrollment created</td><td>{@link #enroll}</td></tr>
 *   <tr><td>200</td><td>state changed successfully</td><td>the action methods</td></tr>
 *   <tr><td>400</td><td>malformed body / failed constraint</td>
 *       <td>{@code ConstraintViolationExceptionMapper}</td></tr>
 *   <tr><td>404</td><td>student, course or enrollment not found</td>
 *       <td>{@code ResourceNotFoundExceptionMapper}</td></tr>
 *   <tr><td>409</td><td>course full, window closed, prerequisites unmet,
 *       already enrolled, illegal transition</td>
 *       <td>{@code BusinessRuleViolationExceptionMapper}</td></tr>
 *   <tr><td>500</td><td>anything unforeseen</td>
 *       <td>{@code GenericExceptionMapper}</td></tr>
 * </table>
 *
 * <p>Notice how few of these appear in the code below. The resource describes
 * the HAPPY PATH; every failure is raised as an exception by the layer that
 * detects it and rendered by a mapper. That is what keeps these methods three
 * lines long instead of thirty.
 */
@Path("/enrollments")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnrollmentResource {

    @Inject
    private EnrollmentService enrollmentService;

    @Inject
    private EnrollmentMapper enrollmentMapper;

    @Context
    private UriInfo uriInfo;

    /**
     * Enrols a student in a course.
     *
     * <p>Every rule - eligibility, window, duplicates, capacity, prerequisites -
     * is enforced by {@code EnrollmentService.enroll}. This method's entire job
     * is to unwrap the request, call it, and build a 201.
     */
    @POST
    public Response enroll(@Valid @NotNull EnrollRequest request) {
        Enrollment enrollment = enrollmentService.enroll(
                request.getStudentId(), request.getCourseId());

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(enrollment.getId()))
                .build();

        return Response.created(location)
                .entity(enrollmentMapper.toResponse(enrollment))
                .build();
    }

    @GET
    @Path("/{id}")
    public EnrollmentResponse findById(@PathParam("id") Long id) {
        return enrollmentMapper.toResponse(enrollmentService.findById(id));
    }

    /**
     * Records a passing exam result.
     *
     * <p>{@code POST} rather than {@code PUT}: recording a grade is not
     * idempotent in the domain sense. The state machine forbids grading an
     * already-COMPLETED enrollment, so a duplicate request is correctly refused
     * with a 409 rather than silently overwriting the first result.
     */
    @POST
    @Path("/{id}/grade")
    public EnrollmentResponse recordGrade(@PathParam("id") Long id,
                                          @Valid @NotNull RecordGradeRequest request) {
        return enrollmentMapper.toResponse(
                enrollmentService.recordPass(id, request.getGrade(), request.isWithHonours()));
    }

    /** Records a failed exam. The seat is kept so the student can retake. */
    @POST
    @Path("/{id}/failure")
    public EnrollmentResponse recordFailure(@PathParam("id") Long id) {
        return enrollmentMapper.toResponse(enrollmentService.recordFailure(id));
    }

    /**
     * Re-activates a FAILED enrollment for a retake.
     *
     * <p>Legal only from FAILED - see the transition table in
     * {@code EnrollmentStatus}. Any other starting state produces a 409 whose
     * message names both states, e.g.
     * {@code "Illegal enrollment transition: COMPLETED -> ACTIVE"}.
     */
    @POST
    @Path("/{id}/retake")
    public EnrollmentResponse retake(@PathParam("id") Long id) {
        return enrollmentMapper.toResponse(enrollmentService.retake(id));
    }

    /**
     * Withdraws from the course.
     *
     * <p>Mapped to {@code DELETE} on the enrollment, because from the client's
     * point of view that is what it is: "remove my enrollment". The server does
     * NOT delete the row - it transitions the status to WITHDRAWN, preserving
     * the academic record.
     *
     * <p>That distinction between the HTTP verb and the physical storage
     * operation is normal and healthy. The API describes what the caller MEANS,
     * not what the database does. Returning 200 with the updated resource,
     * rather than 204, lets the client see the resulting state.
     */
    @DELETE
    @Path("/{id}")
    public EnrollmentResponse withdraw(@PathParam("id") Long id) {
        return enrollmentMapper.toResponse(enrollmentService.withdraw(id));
    }
}
