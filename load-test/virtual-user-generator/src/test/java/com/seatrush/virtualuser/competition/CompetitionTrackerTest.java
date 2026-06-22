package com.seatrush.virtualuser.competition;

import com.seatrush.virtualuser.competition.dto.CompetitionSnapshotResponseDto;
import com.seatrush.virtualuser.competition.dto.CompetitionStartRequestDto;
import com.seatrush.virtualuser.config.VirtualUserProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionTrackerTest {

    @Test
    void trackUserStatusAndCompletion() {
        CompetitionTracker tracker = new CompetitionTracker(properties());
        CompetitionStartRequestDto request = new CompetitionStartRequestDto(
                1L,
                "practice-test",
                10,
                60,
                30,
                2,
                1000,
                new CompetitionStartRequestDto.BehaviorWeights(5, 5, 10, 10, 70)
        );
        tracker.initialize("run-1", request);

        tracker.updateUser(
                1,
                CompetitionStatus.PREPARING,
                CompetitionStatus.READY,
                "로그인 완료"
        );
        tracker.updateUser(
                1,
                CompetitionStatus.READY,
                CompetitionStatus.CONFIRMED,
                "예매 완료"
        );

        CompetitionSnapshotResponseDto snapshot = tracker.snapshot();
        assertThat(snapshot.completedUsers()).isEqualTo(1);
        assertThat(snapshot.userStatuses()).containsEntry("CONFIRMED", 1L);
        assertThat(snapshot.recentEvents()).hasSize(2);
    }

    private VirtualUserProperties properties() {
        return new VirtualUserProperties(
                "https://seat-rush.example.com",
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(10),
                Duration.ofMinutes(20),
                Duration.ofMillis(400),
                Duration.ofSeconds(15),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                5,
                Duration.ofMillis(150),
                2,
                "data/test-accounts.json",
                "seat-rush.local",
                "Virtual-user-password!",
                Duration.ofMinutes(5),
                Duration.ofMinutes(10)
        );
    }
}
