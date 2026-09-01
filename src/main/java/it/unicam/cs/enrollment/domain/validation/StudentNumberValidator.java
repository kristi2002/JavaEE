package it.unicam.cs.enrollment.domain.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * The logic behind {@link StudentNumber}.
 *
 * <p>A {@link ConstraintValidator} is parameterised by the annotation it
 * implements and the type it can validate. The container instantiates it, and -
 * importantly - it is CDI-aware: you may {@code @Inject} a repository here if a
 * rule genuinely needs a database lookup (checking uniqueness, for instance).
 *
 * <h2>The two rules of writing a validator</h2>
 * <ol>
 *   <li><b>{@code null} is always valid.</b> This surprises people, but it is
 *       correct: "is it present?" is {@code @NotNull}'s job, and "is it well
 *       formed?" is this class's job. Keeping them separate means a caller can
 *       compose {@code @NotNull @StudentNumber} for a required field and just
 *       {@code @StudentNumber} for an optional one. If this class rejected
 *       null, the optional case would be impossible to express.</li>
 *   <li><b>Validators must be thread-safe and stateless.</b> One instance
 *       serves concurrent requests. Note the {@code static final} Pattern: it is
 *       immutable and compiled once, rather than re-parsed on every call.</li>
 * </ol>
 */
public class StudentNumberValidator implements ConstraintValidator<StudentNumber, String> {

    /**
     * Compiling the regex once into a {@code static final} field matters more
     * than it looks. {@code String.matches()} compiles a fresh Pattern on every
     * invocation - fine once, wasteful in a validator that runs on every
     * request.
     */
    private static final Pattern MATRICOLA_PATTERN = Pattern.compile("^\\d{6}$");

    /**
     * Called once per validator instance. Use it to read attributes off the
     * annotation (a {@code min}/{@code max}, a flag) into fields. Ours has no
     * attributes, so there is nothing to do - the method is left here, with the
     * default no-op, to document that the hook exists.
     */
    @Override
    public void initialize(StudentNumber constraintAnnotation) {
        // no configurable attributes on this constraint
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Rule 1: absent is not the same as invalid. Let @NotNull decide.
        if (value == null) {
            return true;
        }
        return MATRICOLA_PATTERN.matcher(value).matches();
    }
}
