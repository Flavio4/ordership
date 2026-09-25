package com.rtz.ordership.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPatternsTest {

    @Test
    void containsLikeIsLowercaseAndTrimmed() {
        assertThat(SearchPatterns.containsLike("  María ")).isEqualTo("%maría%");
        assertThat(SearchPatterns.containsLike("#1488")).isEqualTo("%#1488%");
    }

    @Test
    void blankTextMeansNoFilter() {
        assertThat(SearchPatterns.containsLike(null)).isNull();
        assertThat(SearchPatterns.containsLike("   ")).isNull();
        assertThat(SearchPatterns.phoneContainsLike(null)).isNull();
    }

    @Test
    void localPhoneFormatMatchesTheStoredInternationalOne() {
        String pattern = SearchPatterns.phoneContainsLike("0983 625-215");

        assertThat(pattern).isEqualTo("%983625215%");
        assertThat("+595983625215").matches(pattern.replace("%", ".*"));
    }

    @Test
    void internationalPhoneKeepsTheCountryCode() {
        assertThat(SearchPatterns.phoneContainsLike("+595 983 625")).isEqualTo("%595983625%");
    }

    @Test
    void shortNumbersAreNotSearchedAsPhones() {
        assertThat(SearchPatterns.phoneContainsLike("#1488")).isNull();
        assertThat(SearchPatterns.phoneContainsLike("María")).isNull();
    }
}
