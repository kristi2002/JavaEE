package it.unicam.cs.enrollment.domain.model;

/**
 * Administrative standing of a {@link Student}.
 *
 * <p>Only an {@link #ACTIVE} student may enrol in new courses - that single
 * rule is the reason this type exists rather than a boolean {@code enabled}
 * flag. A boolean cannot tell you <i>why</i> someone is disabled, and the
 * difference between "suspended for unpaid fees" and "graduated" matters for
 * reporting, for support staff, and for the rules themselves.
 *
 * <p>Practical note: adding a constant to an enum is a backwards-compatible
 * change for the database (we store the NAME, see
 * {@code @Enumerated(EnumType.STRING)}) but a breaking change for any
 * {@code switch} that does not have a {@code default} branch. Always write the
 * default branch.
 */
public enum StudentStatus {

    /** Regularly enrolled and in good standing. May take new courses. */
    ACTIVE,

    /** Temporarily blocked, e.g. unpaid tuition. Existing enrollments survive. */
    SUSPENDED,

    /** Finished the degree programme. Read-only from here on. */
    GRADUATED,

    /** Left the university. Terminal. */
    WITHDRAWN;

    /** @return whether a student in this state is allowed to take new courses. */
    public boolean canEnroll() {
        return this == ACTIVE;
    }
}
