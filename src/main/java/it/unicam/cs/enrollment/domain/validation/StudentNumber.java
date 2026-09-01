package it.unicam.cs.enrollment.domain.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A CUSTOM BEAN VALIDATION CONSTRAINT: the field must be a well-formed UNICAM
 * matricola (exactly six digits).
 *
 * <h2>Why write your own constraint?</h2>
 * You could scatter {@code @Pattern(regexp = "\\d{6}")} across every DTO and
 * entity that carries a student number. Then the format changes and you have to
 * find all of them. A named constraint gives the rule:
 * <ul>
 *   <li>one definition and one error message;</li>
 *   <li>a name that expresses intent - {@code @StudentNumber} says what it is,
 *       {@code @Pattern} says only how it is checked;</li>
 *   <li>somewhere to put logic a regex cannot express (a checksum, a lookup).</li>
 * </ul>
 *
 * <h2>The three members every constraint must declare</h2>
 * These are required by the specification; omitting one makes the annotation
 * invalid as a constraint:
 * <ul>
 *   <li>{@code message()} - the default violation message. The
 *       {@code {braces}} form is a resource-bundle key, which is how you
 *       internationalise messages; a plain string is used as-is.</li>
 *   <li>{@code groups()} - VALIDATION GROUPS let you validate a subset of
 *       constraints in different situations, e.g. an {@code OnCreate} group
 *       where id must be null and an {@code OnUpdate} group where it must not.</li>
 *   <li>{@code payload()} - metadata carried along with the violation. Rarely
 *       used directly; the classic case is tagging a rule with a severity that
 *       the UI then renders differently.</li>
 * </ul>
 *
 * <h2>Where constraints run</h2>
 * Bean Validation is not something you call - the container triggers it:
 * <ul>
 *   <li>JAX-RS validates {@code @Valid} method parameters before your resource
 *       method body executes;</li>
 *   <li>JPA validates entities on {@code @PrePersist} and {@code @PreUpdate};</li>
 *   <li>CDI validates method arguments and return values on beans.</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = StudentNumberValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StudentNumber {

    String message() default "must be a valid student number: exactly 6 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
