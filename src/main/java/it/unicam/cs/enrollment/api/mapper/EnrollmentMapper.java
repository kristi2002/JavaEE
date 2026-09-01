package it.unicam.cs.enrollment.api.mapper;

import it.unicam.cs.enrollment.api.dto.response.EnrollmentResponse;
import it.unicam.cs.enrollment.domain.model.Enrollment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates {@link Enrollment} entities into their API representation.
 *
 * <h2>Why mapping deserves its own class</h2>
 * The conversion has to live somewhere. The three candidates:
 * <ul>
 *   <li><b>On the entity</b> ({@code enrollment.toResponse()}) - makes the
 *       domain model depend on the API layer. Backwards.</li>
 *   <li><b>On the DTO</b> ({@code EnrollmentResponse.from(entity)}) - workable,
 *       and common. But a static method cannot inject anything, so the moment a
 *       mapping needs a lookup or a formatter you have to restructure.</li>
 *   <li><b>In a dedicated mapper bean</b> - what we do here. Injectable,
 *       independently testable, and the resource classes stay short.</li>
 * </ul>
 *
 * <h2>Hand-written, and why</h2>
 * Real projects often use MAPSTRUCT, which generates this code from an
 * interface at compile time - fast, type-safe, no reflection. It is the right
 * choice on a large codebase with dozens of DTOs.
 *
 * <p>Written by hand here because the goal is to SEE the layer boundary. Once
 * generation hides it, it becomes very easy to "just add the field to both
 * sides" and gradually erase the distinction the DTO exists to create.
 *
 * <p>Avoid the third option, reflection-based mappers like Dozer or ModelMapper:
 * they match fields by name at runtime, so renaming a field breaks the mapping
 * silently, with no compile error and often no test failure.
 */
@ApplicationScoped
public class EnrollmentMapper {

    /**
     * Maps one enrollment.
     *
     * <p><b>Precondition:</b> {@code student} and {@code course} must already be
     * loaded. This method is called after the transaction has committed, so
     * touching an uninitialised lazy proxy here throws
     * {@code LazyInitializationException}. The repository queries feeding this
     * mapper all use {@code JOIN FETCH} for exactly that reason.
     */
    public EnrollmentResponse toResponse(Enrollment enrollment) {
        EnrollmentResponse response = new EnrollmentResponse();

        response.setId(enrollment.getId());

        response.setStudentId(enrollment.getStudent().getId());
        response.setStudentNumber(enrollment.getStudent().getStudentNumber());
        response.setStudentName(enrollment.getStudent().fullName());

        response.setCourseId(enrollment.getCourse().getId());
        response.setCourseCode(enrollment.getCourse().getCode());
        response.setCourseTitle(enrollment.getCourse().getTitle());
        response.setCourseCredits(enrollment.getCourse().getCredits());

        // Enum to String at the boundary. Sending `status.name()` rather than
        // the enum object keeps the JSON stable if the Java type is ever
        // refactored, and makes the contract obviously a string to clients in
        // other languages.
        response.setStatus(enrollment.getStatus().name());

        response.setEnrolledAt(enrollment.getEnrolledAt());
        response.setCompletedAt(enrollment.getCompletedAt());
        response.setGrade(enrollment.getGrade());
        response.setWithHonours(enrollment.isWithHonours());
        response.setFormattedGrade(enrollment.formattedGrade());

        return response;
    }

    /**
     * Maps a list. A one-liner, but having it here means no resource class ever
     * writes the stream pipeline itself - and the day mapping needs a shared
     * lookup, there is one place to add it.
     */
    public List<EnrollmentResponse> toResponseList(List<Enrollment> enrollments) {
        return enrollments.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
