package com.example.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.TableMetadata;
import com.example.warehouse.model.TableOpsRequest;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.MonitorResultRepository;
import com.example.warehouse.repository.TaskExecutionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TableOpsServiceTest {
    @Test
    void submitsBackfillAsAsyncTask() {
        MetadataService metadataService = mock(MetadataService.class);
        TaskExecutionService taskExecutionService = mock(TaskExecutionService.class);
        TaskExecutionRepository executionRepository = mock(TaskExecutionRepository.class);
        MonitorResultRepository monitorRepository = mock(MonitorResultRepository.class);
        TableOpsService service = new TableOpsService(
                metadataService, taskExecutionService, executionRepository, monitorRepository);
        TableMetadata table = new TableMetadata();
        table.setDatabaseName("basiccomment");
        table.setTableName("avatar_commentbatchsource");
        table.setOdsTable("ods_basiccomment_avatar_commentbatchsource_dic");
        table.setPartitionColumn("ctime");
        when(metadataService.findTable("basiccomment", "avatar_commentbatchsource"))
                .thenReturn(Optional.of(table));
        TaskExecution execution = new TaskExecution();
        execution.setId(51L);
        when(taskExecutionService.submitShell(
                eq("backfill_basiccomment_avatar_commentbatchsource"),
                eq("BACKFILL"),
                contains("2026-10-01"),
                eq(1800),
                eq("alice"),
                isNull(),
                isNull())).thenReturn(execution);
        TableOpsRequest request = new TableOpsRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setStartDt("2026-10-01");
        request.setEndDt("2026-10-02");
        request.setDryRun(false);

        Object submitted = service.backfill(request, "alice");

        assertSame(execution, submitted);
    }
}
