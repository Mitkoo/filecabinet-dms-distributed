package com.filecabinet.extraction.scheduling;

import com.filecabinet.extraction.job.service.ExtractionJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionSchedulerTest {

    @Mock
    private ExtractionJobService jobService;

    @InjectMocks
    private ExtractionScheduler scheduler;

    @Test
    void drainQueueProcessesEachQueuedJob() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(jobService.findQueuedJobIds()).thenReturn(List.of(a, b));

        scheduler.drainQueue();

        verify(jobService).processJob(a);
        verify(jobService).processJob(b);
    }

    @Test
    void drainQueueDoesNothingWhenEmpty() {
        when(jobService.findQueuedJobIds()).thenReturn(List.of());
        scheduler.drainQueue();
        verify(jobService, never()).processJob(any());
    }

    @Test
    void nightlyMaintenanceRunsPurgeAndReset() {
        scheduler.nightlyMaintenance();
        verify(jobService, times(1)).purgeCompletedOlderThan(any());
        verify(jobService, times(1)).resetStuckProcessing(any());
    }
}
