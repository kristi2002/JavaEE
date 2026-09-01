package it.unicam.cs.enrollment.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the custom {@link StudentNumber} constraint.
 *
 * <h2>{@code @ParameterizedTest} - one test, many inputs</h2>
 * The alternative is eight nearly identical methods, or one method containing
 * eight assertions where the first failure hides the rest. A parameterized test
 * reports each input as its own case, so a failure names the exact value that
 * broke.
 *
 * <p>The sources worth knowing: {@code @ValueSource} (literals),
 * {@code @NullSource} / {@code @EmptySource}, {@code @EnumSource},
 * {@code @CsvSource} (several arguments per case) and {@code @MethodSource}
 * (arbitrary objects from a factory method).
 */
@DisplayName("StudentNumberValidator")
class StudentNumberValidatorTest {

    private final StudentNumberValidator validator = new StudentNumberValidator();

    @ParameterizedTest(name = "\"{0}\" is a valid matricola")
    @ValueSource(strings = {"100001", "000000", "999999", "123456"})
    @DisplayName("accepts exactly six digits")
    void shouldAcceptSixDigits(String value) {
        // The ConstraintValidatorContext is only needed for CUSTOMISING the
        // violation message. Passing null is safe here and keeps the test free
        // of mocking machinery it does not need.
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {
            "12345",       // too short
            "1234567",     // too long
            "12345A",      // contains a letter
            "12 345",      // contains a space
            "",            // empty
            " 123456",     // leading whitespace - not trimmed on purpose
            "12345.",      // punctuation
    })
    @DisplayName("rejects anything that is not exactly six digits")
    void shouldRejectMalformedValues(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    /**
     * The single most important test in this class, and the one people get
     * wrong.
     *
     * <p>A validator must treat {@code null} as VALID. Presence is
     * {@code @NotNull}'s responsibility; format is this class's. Keeping them
     * separate is what allows a field to be optional-but-well-formed. If this
     * test ever starts failing because someone "fixed" the null handling, the
     * failure message should explain why it was not a bug.
     */
    @ParameterizedTest
    @NullSource
    @DisplayName("treats null as valid - presence is @NotNull's job, not ours")
    void shouldTreatNullAsValid(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }
}
