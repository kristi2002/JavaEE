package it.unicam.cs.enrollment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * A VALUE OBJECT representing an email address.
 *
 * <h2>Entity vs Value Object - a core Domain-Driven Design distinction</h2>
 * <ul>
 *   <li>An <b>Entity</b> has identity that persists through change. Student
 *       #42 is still Student #42 after she changes her name.</li>
 *   <li>A <b>Value Object</b> has no identity; it <i>is</i> its value. Two
 *       {@code Email}s holding "a@b.it" are interchangeable, exactly like two
 *       {@code Integer}s holding 5.</li>
 * </ul>
 *
 * <h2>Why not just use a String?</h2>
 * This is the "primitive obsession" code smell. A {@code String} field can hold
 * "hello world", can be swapped with the firstName parameter without the
 * compiler noticing, and forces every caller to re-validate. Wrapping it:
 * <ul>
 *   <li>makes invalid states unrepresentable - you cannot construct an
 *       {@code Email} that is not an email;</li>
 *   <li>gives validation and normalisation a single home;</li>
 *   <li>gives the type system something to check.</li>
 * </ul>
 *
 * <h2>{@code @Embeddable} - how JPA persists a value object</h2>
 * An embeddable has no table and no id. Its fields are stored as extra columns
 * inside the OWNING entity's table. So {@code Student.email.value} becomes the
 * {@code students.email} column - one table, no join, and the object model
 * stays expressive.
 *
 * <p>The class is deliberately IMMUTABLE: no setters, private constructor,
 * static factory. Immutable value objects are automatically thread-safe and
 * cannot be corrupted after validation.
 */
@Embeddable
public class Email implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The {@code @Email} constraint below is
     * {@code jakarta.validation.constraints.Email} - fully qualified because
     * this class shares its simple name.
     */
    @NotBlank(message = "{email.notblank}")
    @jakarta.validation.constraints.Email(message = "{email.invalid}")
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    private String value;

    /**
     * JPA requires a no-argument constructor to instantiate the embeddable via
     * reflection. It is {@code protected} rather than {@code public} so that
     * application code is pushed towards the validating factory method.
     */
    protected Email() {
        // required by JPA
    }

    private Email(String value) {
        this.value = value;
    }

    /**
     * STATIC FACTORY METHOD. Preferred over a public constructor because it has
     * a meaningful name, can normalise its input, and can return a cached
     * instance if that ever becomes worthwhile.
     *
     * <p>Normalising to lower case here means the uniqueness rule works: without
     * it, "Mario@unicam.it" and "mario@unicam.it" would be two different rows.
     * Normalise at the boundary, once.
     */
    public static Email of(String raw) {
        Objects.requireNonNull(raw, "email must not be null");
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return new Email(normalised);
    }

    public String getValue() {
        return value;
    }

    /** The part after the '@'. Useful for rules like "must be a unicam.it address". */
    public String domain() {
        int at = value.indexOf('@');
        return at >= 0 ? value.substring(at + 1) : "";
    }

    /**
     * Value objects MUST implement equals/hashCode by value - that is what
     * makes them values. Compare with {@link BaseEntity}, where equality is by
     * identity instead.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Email)) {
            return false;
        }
        return Objects.equals(value, ((Email) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
