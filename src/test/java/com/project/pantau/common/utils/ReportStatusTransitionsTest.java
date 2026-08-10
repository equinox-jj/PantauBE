package com.project.pantau.common.utils;

import com.project.pantau.enums.ReportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustively verifies the allowed transition table:
 * <p>
 * REPORTED -> ACKNOWLEDGED | REJECTED
 * ACKNOWLEDGED -> IN_PROGRESS | REJECTED
 * IN_PROGRESS -> RESOLVED | REJECTED
 * RESOLVED -> CLOSED
 * CLOSED -> (none, terminal)
 * REJECTED -> (none, terminal)
 */
class ReportStatusTransitionsTest {

    // Independently re-derived expectation table, mirroring the source's ALLOWED map.
    private static final Map<ReportStatus, Set<ReportStatus>> EXPECTED = new EnumMap<>(ReportStatus.class);

    static {
        EXPECTED.put(ReportStatus.REPORTED, EnumSet.of(ReportStatus.ACKNOWLEDGED, ReportStatus.REJECTED));
        EXPECTED.put(ReportStatus.ACKNOWLEDGED, EnumSet.of(ReportStatus.IN_PROGRESS, ReportStatus.REJECTED));
        EXPECTED.put(ReportStatus.IN_PROGRESS, EnumSet.of(ReportStatus.RESOLVED, ReportStatus.REJECTED));
        EXPECTED.put(ReportStatus.RESOLVED, EnumSet.of(ReportStatus.CLOSED));
        EXPECTED.put(ReportStatus.CLOSED, EnumSet.noneOf(ReportStatus.class));
        EXPECTED.put(ReportStatus.REJECTED, EnumSet.noneOf(ReportStatus.class));
    }

    @Test
    @DisplayName("isAllowed matches the expected transition table for every (from, to) pair")
    void isAllowed_matchesExpectedTableForAllPairs() {
        for (ReportStatus from : ReportStatus.values()) {
            for (ReportStatus to : ReportStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                boolean actual = ReportStatusTransitions.isAllowed(from, to);

                assertThat(actual)
                        .as("isAllowed(%s, %s)", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("valid transitions are individually allowed")
    void isAllowed_validTransitions() {
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.ACKNOWLEDGED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.IN_PROGRESS)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.IN_PROGRESS, ReportStatus.RESOLVED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.IN_PROGRESS, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.RESOLVED, ReportStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("skipping states is rejected, e.g. REPORTED -> IN_PROGRESS")
    void isAllowed_rejectsSkippedStates() {
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.IN_PROGRESS)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.RESOLVED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.CLOSED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.RESOLVED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.CLOSED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.IN_PROGRESS, ReportStatus.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("backward transitions are rejected")
    void isAllowed_rejectsBackwardTransitions() {
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.REPORTED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.IN_PROGRESS, ReportStatus.ACKNOWLEDGED)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.RESOLVED, ReportStatus.IN_PROGRESS)).isFalse();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.CLOSED, ReportStatus.RESOLVED)).isFalse();
    }

    @Test
    @DisplayName("self-transitions are rejected for every state")
    void isAllowed_rejectsSelfTransitions() {
        for (ReportStatus status : ReportStatus.values()) {
            assertThat(ReportStatusTransitions.isAllowed(status, status))
                    .as("isAllowed(%s, %s)", status, status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("terminal states (CLOSED, REJECTED) allow no further transitions")
    void isAllowed_terminalStatesHaveNoTransitions() {
        for (ReportStatus to : ReportStatus.values()) {
            assertThat(ReportStatusTransitions.isAllowed(ReportStatus.CLOSED, to)).isFalse();
            assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REJECTED, to)).isFalse();
        }
    }

    @Test
    @DisplayName("REJECTED is reachable from every pre-RESOLVED state")
    void isAllowed_rejectedReachableFromPreResolvedStates() {
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.REPORTED, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.ACKNOWLEDGED, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.IN_PROGRESS, ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatusTransitions.isAllowed(ReportStatus.RESOLVED, ReportStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("requiresNote is true only when transitioning to REJECTED")
    void requiresNote_trueOnlyForRejected() {
        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.REJECTED)).isTrue();

        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.REPORTED)).isFalse();
        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.ACKNOWLEDGED)).isFalse();
        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.IN_PROGRESS)).isFalse();
        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.RESOLVED)).isFalse();
        assertThat(ReportStatusTransitions.requiresNote(ReportStatus.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("requiresNote returns false for a null target status")
    void requiresNote_nullIsFalse() {
        assertThat(ReportStatusTransitions.requiresNote(null)).isFalse();
    }
}
