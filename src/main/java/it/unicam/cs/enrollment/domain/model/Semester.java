package it.unicam.cs.enrollment.domain.model;

/**
 * The teaching period a course belongs to.
 *
 * <p>This enum shows the pattern of attaching DATA and BEHAVIOUR to constants -
 * something an {@code int} constant or a {@code String} could never do. Each
 * constant carries its own display label, and the type exposes a method that
 * operates on it.
 *
 * <p>In the Italian system these are the two {@code semestri}; the codebase
 * keeps English identifiers (the industry default for source code) while the
 * label carries the local wording used in the UI.
 */
public enum Semester {

    FALL("First semester", 1),
    SPRING("Second semester", 2);

    private final String displayName;
    private final int ordinalNumber;

    /**
     * Enum constructors are implicitly private and run once, at class
     * initialisation. Enum constants are therefore the simplest correct
     * singletons in Java - which is why {@code enum} is the recommended way to
     * write a singleton at all.
     */
    Semester(String displayName, int ordinalNumber) {
        this.displayName = displayName;
        this.ordinalNumber = ordinalNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getOrdinalNumber() {
        return ordinalNumber;
    }

    /**
     * @return the semester that follows this one. Deliberately does not wrap
     *         across academic years - that is the caller's concern.
     */
    public Semester next() {
        return this == FALL ? SPRING : FALL;
    }
}
