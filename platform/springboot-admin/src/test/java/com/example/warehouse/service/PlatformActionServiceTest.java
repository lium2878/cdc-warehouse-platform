package com.example.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.ActionRequest;
import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TaskExecution;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlatformActionServiceTest {
    @Test
    void submitsLongActionAsAsyncTask() {
        CommandExecutorService commandExecutor = mock(CommandExecutorService.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        PlatformActionService service = new PlatformActionService(commandExecutor, taskExecutionService);
        TaskExecution execution = new TaskExecution();
        execution.setId(71L);
        when(taskExecutionService.submitShell(
                eq("daily_merge"), eq("MERGE"), contains("2026-10-13"),
                eq(1800), eq("alice"), isNull())).thenReturn(execution);
        ActionRequest request = new ActionRequest();
        request.setBizDt("2026-10-13");

        Object result = service.run("daily-merge", request, "alice");

        assertSame(execution, result);
    }

    @Test
    void keepsShortProbeSynchronous() {
        CommandExecutorService commandExecutor = mock(CommandExecutorService.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        PlatformActionService service = new PlatformActionService(commandExecutor, taskExecutionService);
        CommandResult commandResult = new CommandResult(0, "refreshed");
        when(commandExecutor.run(Arrays.asList("bash", "scripts/refresh_ops_snapshot.sh"), 60))
                .thenReturn(commandResult);

        Object result = service.run("refresh-ops", null, "alice");

        assertSame(commandResult, result);
        verify(commandExecutor).run(anyList(), eq(60L));
    }

    @Test
    void rejectsInvalidDolphinSchedulerMode() {
        PlatformActionService service = new PlatformActionService(
                mock(CommandExecutorService.class), mock(TaskExecutionService.class));
        ActionRequest request = new ActionRequest();
        request.setMode("--delete");

        assertThrows(IllegalArgumentException.class,
                () -> service.run("ds-publish", request, "alice"));
    }
}
