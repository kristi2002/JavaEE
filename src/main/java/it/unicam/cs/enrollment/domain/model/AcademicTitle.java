package it.unicam.cs.enrollment.domain.model;

/**
 * Academic rank of a {@link Professor}.
 *
 * <p>Included mostly to show a plain, well-named enum used as a
 * {@code @Enumerated(EnumType.STRING)} column. Note the ordering of the
 * constants: enums have a natural order (declaration order) which
 * {@code Comparable}, {@code EnumSet} and {@code ORDER BY} all rely on, so
 * declare them in a meaningful sequence from the start. Reordering them later
 * is a breaking change for anything that persisted {@code ordinal()}.
 */
public enum AcademicTitle {

    /** {@code Ricercatore} - entry-level research and teaching post. */
    ASSISTANT_PROFESSOR("Ricercatore"),

    /** {@code Professore Associato}. */
    ASSOCIATE_PROFESSOR("Professore Associato"),

    /** {@code Professore Ordinario} - the most senior rank. */
    FULL_PROFESSOR("Professore Ordinario");

    private final String italianTitle;

    AcademicTitle(String italianTitle) {
        this.italianTitle = italianTitle;
    }

    public String getItalianTitle() {
        return italianTitle;
    }
}
