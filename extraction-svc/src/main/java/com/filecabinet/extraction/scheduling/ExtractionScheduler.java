package com.filecabinet.extraction.scheduling;

import com.filecabinet.extraction.job.service.ExtractionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionScheduler {

    private final ExtractionJobService jobService;

    @Scheduled(fixedDelay = 10000)
    public void drainQueue() {
        List<UUID> queued = jobService.findQueuedJobIds();
        if (queued.isEmpty()) {
            return;
        }
        log.info("Draining {} queued extraction jobs", queued.size());
        for (UUID jobId : queued) {
            jobService.processJob(jobId);
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void nightlyMaintenance() {
        LocalDateTime now = LocalDateTime.now();
        jobService.purgeCompletedOlderThan(now.minusDays(30));
        jobService.resetStuckProcessing(now.minusHours(1));
    }
}
