package com.example.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.OnboardRequest;
import com.example.warehouse.model.SparkTaskConfig;
import com.example.warehouse.model.TableMetadata;
import com.example.warehouse.model.TaskExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OnboardingServiceTest {
    @Test
    void registersMetadataAndTaskOnlyFromSuccessHandler() throws Exception {
        CommandExecutorService commandExecutor = mock(CommandExecutorService.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        OnboardingRegistrationService registrationService = mock(OnboardingRegistrationService.class);
        when(commandExecutor.getProjectRoot()).thenReturn(new File("../.."));
        TaskExecution execution = new TaskExecution();
        execution.setId(81L);
        ArgumentCaptor<TaskExecutionService.SuccessHandler> handler =
                ArgumentCaptor.forClass(TaskExecutionService.SuccessHandler.class);
        when(taskExecutionService.submitShellWithSuccessHandler(
                eq("onboarding_basiccomment_avatar_commentbatchsource"),
                eq("ONBOARDING"),
                contains("scripts/onboard_table.py"),
                eq(1800),
                eq("alice"),
                isNull(),
                handler.capture())).thenReturn(execution);
        OnboardingService service = new OnboardingService(
                commandExecutor, taskExecutionService, registrationService, new ObjectMapper());

        TaskExecution submitted = service.execute(request(), "alice");
        handler.getValue().onSuccess();

        assertEquals(81L, submitted.getId());
        ArgumentCaptor<TableMetadata> table = ArgumentCaptor.forClass(TableMetadata.class);
        ArgumentCaptor<SparkTaskConfig> task = ArgumentCaptor.forClass(SparkTaskConfig.class);
        verify(registrationService).register(table.capture(), task.capture());
        assertEquals("ods_basiccomment_avatar_commentbatchsource_dic", table.getValue().getOdsTable());
        assertEquals("merge_ods_basiccomment_avatar_commentbatchsource_dic", task.getValue().getTaskName());
    }

    private OnboardRequest request() {
        OnboardRequest request = new OnboardRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setDbaMetadataPath("metadata/dba/basiccomment.avatar_commentbatchsource.json");
        request.setPrimaryKeys("id");
        request.setVersionColumn("ver");
        request.setPartitionColumn("ctime");
        return request;
    }
}
