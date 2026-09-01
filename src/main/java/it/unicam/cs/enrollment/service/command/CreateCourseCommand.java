package it.unicam.cs.enrollment.service.command;

import it.unicam.cs.enrollment.domain.model.Semester;

import java.time.Instant;
import java.util.List;

/**
 * Input for the "create a course" use case.
 *
 * <p>Note that {@code prerequisiteCourseIds} is a list of IDs, not a list of
 * {@code Course} objects. A command carries plain data across a boundary; it is
 * the service's job to resolve those ids into entities inside its transaction.
 * A command holding managed entities would drag persistence-context lifetime
 * into a layer that has no business knowing about it.
 */
public final class CreateCourseCommand {

    private final String code;
    private final String title;
    private final String description;
    private final int credits;
    private final int capacity;
    private final Semester semester;
    private final int academicYear;
    private final Long professorId;
    private final Instant enrollmentOpensAt;
    private final Instant enrollmentClosesAt;
    private final List<Long> prerequisiteCourseIds;

    public CreateCourseCommand(String code,
                               String title,
                               String description,
                               int credits,
                               int capacity,
                               Semester semester,
                               int academicYear,
                               Long professorId,
                               Instant enrollmentOpensAt,
                               Instant enrollmentClosesAt,
                               List<Long> prerequisiteCourseIds) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.credits = credits;
        this.capacity = capacity;
        this.semester = semester;
        this.academicYear = academicYear;
        this.professorId = professorId;
        this.enrollmentOpensAt = enrollmentOpensAt;
        this.enrollmentClosesAt = enrollmentClosesAt;
        // Never store the caller's list directly: they could mutate it afterwards
        // and change this "immutable" command from the outside.
        this.prerequisiteCourseIds = prerequisiteCourseIds == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(prerequisiteCourseIds));
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getCredits() {
        return credits;
    }

    public int getCapacity() {
        return capacity;
    }

    public Semester getSemester() {
        return semester;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public Long getProfessorId() {
        return professorId;
    }

    public Instant getEnrollmentOpensAt() {
        return enrollmentOpensAt;
    }

    public Instant getEnrollmentClosesAt() {
        return enrollmentClosesAt;
    }

    public List<Long> getPrerequisiteCourseIds() {
        return prerequisiteCourseIds;
    }
}
