package com.example.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.ReplayRequest;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.ReplayRepository;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {
    @Test
    void linksReplayRecordToAsyncExecution() {
        ReplayRepository repository = mock(ReplayRepository.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        ReplayService service = new ReplayService(repository, taskExecutionService);
        ReplayRequest request = request();
        TaskExecution execution = new TaskExecution();
        execution.setId(42L);
        execution.setStatus("PENDING");
        when(repository.save(eq(request), any(String.class))).thenReturn(7L);
        when(taskExecutionService.submitShell(
                eq("replay_basiccomment_avatar_commentbatchsource"),
                eq("REPLAY"),
                any(String.class),
                eq(1800),
                eq("alice"),
                isNull())).thenReturn(execution);

        TaskExecution submitted = service.execute(request, "alice");

        assertEquals(42L, submitted.getId());
        verify(repository).attachExecution(7L, 42L);
    }

    @Test
    void marksReplayRejectedWhenTaskIsAlreadyRunning() {
        ReplayRepository repository = mock(ReplayRepository.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        ReplayService service = new ReplayService(repository, taskExecutionService);
        ReplayRequest request = request();
        when(repository.save(eq(request), any(String.class))).thenReturn(8L);
        when(taskExecutionService.submitShell(
                any(String.class), any(String.class), any(String.class),
                eq(1800), eq("alice"), isNull()))
                .thenThrow(new IllegalStateException("already running"));

        assertThrows(IllegalStateException.class, () -> service.execute(request, "alice"));
        verify(repository).updateStatus(8L, "REJECTED");
    }

    private ReplayRequest request() {
        ReplayRequest request = new ReplayRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setStartTime("2026-10-03 00:00:00");
        request.setEndTime("2026-10-04 00:00:00");
        return request;
    }
}
