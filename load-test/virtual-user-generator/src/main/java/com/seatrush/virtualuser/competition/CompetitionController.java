package com.seatrush.virtualuser.competition;

import com.seatrush.virtualuser.competition.dto.CompetitionSnapshotResponseDto;
import com.seatrush.virtualuser.competition.dto.CompetitionStartRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @PostMapping
    public CompetitionSnapshotResponseDto start(
            @Valid @RequestBody CompetitionStartRequestDto request
    ) {
        return competitionService.start(request);
    }

    @GetMapping
    public CompetitionSnapshotResponseDto getStatus() {
        return competitionService.getStatus();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CompetitionSnapshotResponseDto> getEvents() {
        return competitionService.getEvents();
    }

    @DeleteMapping
    public void stop() {
        competitionService.stop();
    }
}
