package it.unicam.cs.enrollment.api.mapper;

import it.unicam.cs.enrollment.api.dto.request.CreateStudentRequest;
import it.unicam.cs.enrollment.api.dto.response.StudentResponse;
import it.unicam.cs.enrollment.domain.model.Student;
import it.unicam.cs.enrollment.service.command.CreateStudentCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converts between {@link Student} entities, API DTOs and service commands.
 *
 * <p>Note the mapper handles BOTH directions:
 * <ul>
 *   <li>inbound - {@code CreateStudentRequest} to {@code CreateStudentCommand},
 *       so the resource never constructs a command by hand;</li>
 *   <li>outbound - {@code Student} to {@code StudentResponse}.</li>
 * </ul>
 * Keeping both here means the whole translation between "HTTP world" and
 * "application world" is readable in one file.
 */
@ApplicationScoped
public class StudentMapper {

    private EnrollmentMapper enrollmentMapper;

    protected StudentMapper() {
        // required by CDI
    }

    @Inject
    public StudentMapper(EnrollmentMapper enrollmentMapper) {
        this.enrollmentMapper = enrollmentMapper;
    }

    /** Inbound: HTTP request to service command. */
    public CreateStudentCommand toCommand(CreateStudentRequest request) {
        return new CreateStudentCommand(
                request.getStudentNumber(),
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                request.getDateOfBirth(),
                request.getEnrollmentYear());
    }

    /**
     * Outbound SUMMARY view, used by list endpoints.
     *
     * <p>Deliberately does NOT touch {@code getEnrollments()}: the list query
     * does not fetch that collection, and reading it here would either throw or
     * fire one query per student in the page - the N+1 problem in its most
     * common disguise.
     *
     * <p>{@code earnedCredits} and {@code weightedAverage} are left null, so
     * JSON-B omits them rather than reporting a misleading zero.
     */
    public StudentResponse toSummaryResponse(Student student) {
        StudentResponse response = new StudentResponse();

        response.setId(student.getId());
        response.setStudentNumber(student.getStudentNumber());
        response.setFirstName(student.getFirstName());
        response.setLastName(student.getLastName());
        response.setFullName(student.fullName());
        response.setEmail(student.getEmail() != null ? student.getEmail().getValue() : null);
        response.setDateOfBirth(student.getDateOfBirth());
        response.setStatus(student.getStatus().name());
        response.setEnrollmentYear(student.getEnrollmentYear());
        response.setCreatedAt(student.getCreatedAt());

        return response;
    }

    /**
     * Outbound DETAIL view: everything in the summary, plus the transcript and
     * the figures derived from it.
     *
     * <p><b>Precondition:</b> the student must have been loaded with
     * {@code StudentRepository.findByIdWithEnrollments}, which fetch-joins the
     * enrollments, their courses and those courses' professors.
     */
    public StudentResponse toDetailResponse(Student student) {
        StudentResponse response = toSummaryResponse(student);

        // Sorted newest-first so the API returns a STABLE, meaningful order.
        // An unordered collection means two identical requests can return the
        // same items in different sequences, which breaks client-side diffing
        // and makes tests intermittently fail.
        List<it.unicam.cs.enrollment.domain.model.Enrollment> sorted =
                new ArrayList<>(student.getEnrollments());
        sorted.sort(Comparator.comparing(
                it.unicam.cs.enrollment.domain.model.Enrollment::getEnrolledAt).reversed());

        response.setEnrollments(enrollmentMapper.toResponseList(sorted));
        response.setEarnedCredits(student.earnedCredits());
        response.setWeightedAverage(round2(student.weightedAverage()));

        return response;
    }

    /**
     * Rounds to two decimals for presentation.
     *
     * <p>{@code double} is fine for a display average. It would NOT be fine for
     * money - {@code 0.1 + 0.2 != 0.3} in binary floating point, and financial
     * code must use {@code BigDecimal}. Knowing which of the two you are dealing
     * with is a habit worth forming.
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
