package com.rtz.ordership.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0983625215,        +595983625215",
            "0983 625-215,      +595983625215",
            "983625215,         +595983625215",
            "595983625215,      +595983625215",
            "+595983625215,     +595983625215",
            "+595 983 625 215,  +595983625215",
            "00595983625215,    +595983625215",
            "+5491155551234,    +5491155551234",
            "12345,             12345"
    })
    void normalizesToInternationalFormat(String input, String expected) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL", "''", "'   '" }, nullValues = "NULL")
    void blankStaysBlank(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo(input);
    }
}
