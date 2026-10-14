package com.example.warehouse.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.TaskExecutionRepository;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class TaskExecutionServiceTest {
    @Test
    void marksTaskFailedWhenSuccessPostProcessingFails() {
        TaskExecutionRepository repository = mock(TaskExecutionRepository.class);
        CommandExecutorService commandExecutor = mock(CommandExecutorService.class);
        Executor directExecutor = Runnable::run;
        TaskExecution execution = new TaskExecution();
        execution.setId(91L);
        execution.setStatus("PENDING");
        when(repository.createPending(
                eq("onboarding_db_table"), eq("ONBOARDING"), eq("true"), eq("{}"),
                eq(30), eq("alice"), eq(null), eq("onboarding_db_table"))).thenReturn(91L);
        when(repository.markRunning(eq(91L), any(String.class))).thenReturn(true);
        when(repository.findById(91L)).thenReturn(Optional.of(execution));
        when(commandExecutor.getProjectRoot()).thenReturn(new File("target"));
        when(commandExecutor.runManaged(
                eq(91L), anyList(), eq(30L), any(File.class), any(Runnable.class)))
                .thenReturn(new CommandResult(0, "command output"));
        TaskExecutionService service = new TaskExecutionService(repository, commandExecutor, directExecutor);

        service.submitShellWithSuccessHandler(
                "onboarding_db_table", "ONBOARDING", "true", 30, "alice", null,
                () -> { throw new IllegalStateException("metadata write failed"); });

        verify(repository).finish(
                eq(91L), eq("FAILED"), eq(1), eq("command output"), anyLong(),
                contains("post-processing failed: metadata write failed"));
    }
}
