package com.example.warehouse.service;

import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TableMetadata;
import com.example.warehouse.model.TableOpsRequest;
import com.example.warehouse.repository.MonitorResultRepository;
import com.example.warehouse.repository.TaskExecutionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TableOpsService {
    private final MetadataService metadataService;
    private final TaskExecutionService taskExecutionService;
    private final TaskExecutionRepository taskExecutionRepository;
    private final MonitorResultRepository monitorResultRepository;

    public TableOpsService(MetadataService metadataService,
                           TaskExecutionService taskExecutionService,
                           TaskExecutionRepository taskExecutionRepository,
                           MonitorResultRepository monitorResultRepository) {
        this.metadataService = metadataService;
        this.taskExecutionService = taskExecutionService;
        this.taskExecutionRepository = taskExecutionRepository;
        this.monitorResultRepository = monitorResultRepository;
    }

    public Object backfill(TableOpsRequest request, String triggeredBy) {
        Optional<TableMetadata> table = findTable(request);
        if (!table.isPresent()) {
            return new CommandResult(2, "table metadata not found");
        }
        String start = valueOrDefault(request.getStartDt(), valueOrDefault(request.getBizDt(), LocalDate.now().minusDays(1).toString()));
        String end = valueOrDefault(request.getEndDt(), start);
        String command = "bash scripts/table_ops.sh backfill "
                + q(table.get().getDatabaseName()) + " "
                + q(table.get().getTableName()) + " "
                + q(start) + " "
                + q(end);
        return submit("backfill_" + request.getDatabaseName() + "_" + request.getTableName(), "BACKFILL", command, 1800, request, triggeredBy, null);
    }

    public Object checkLineage(TableOpsRequest request, String triggeredBy) {
        Optional<TableMetadata> table = findTable(request);
        if (!table.isPresent()) {
            return new CommandResult(2, "table metadata not found");
        }
        String dt = valueOrDefault(request.getBizDt(), LocalDate.now().minusDays(1).toString());
        String command = "bash scripts/table_ops.sh check-lineage "
                + q(table.get().getDatabaseName()) + " "
                + q(table.get().getTableName()) + " "
                + q(dt) + " "
                + q(table.get().getOdsTable());
        return submit("check_lineage_" + request.getDatabaseName() + "_" + request.getTableName(), "CHECK", command, 300, request, triggeredBy, null);
    }

    public Object consistency(TableOpsRequest request, String triggeredBy) {
        Optional<TableMetadata> table = findTable(request);
        if (!table.isPresent()) {
            return new CommandResult(2, "table metadata not found");
        }
        String dt = valueOrDefault(request.getBizDt(), LocalDate.now().minusDays(1).toString());
        String command = "bash scripts/table_ops.sh consistency "
                + q(table.get().getDatabaseName()) + " "
                + q(table.get().getTableName()) + " "
                + q(dt) + " "
                + q(table.get().getOdsTable()) + " "
                + q(table.get().getPartitionColumn());
        TaskExecutionService.CompletionHandler completion = execution -> monitorResultRepository.save(
                "row_count_consistency",
                table.get().getDatabaseName(),
                table.get().getTableName(),
                "SUCCESS".equals(execution.getStatus()) ? "OK" : "WARN",
                execution.getOutputExcerpt(),
                "dt=" + dt);
        return submit("consistency_" + request.getDatabaseName() + "_" + request.getTableName(), "MONITOR", command, 300, request, triggeredBy, completion);
    }

    public Object onboardingVerify(TableOpsRequest request, String triggeredBy) {
        Optional<TableMetadata> table = findTable(request);
        if (!table.isPresent()) {
            return new CommandResult(2, "table metadata not found. Run onboarding first.");
        }
        String dt = valueOrDefault(request.getBizDt(), valueOrDefault(request.getStartDt(), LocalDate.now().minusDays(1).toString()));
        String command = "bash scripts/table_ops.sh onboarding-verify "
                + q(table.get().getDatabaseName()) + " "
                + q(table.get().getTableName()) + " "
                + q(dt) + " "
                + q(table.get().getOdsTable());
        return submit("onboarding_verify_" + request.getDatabaseName() + "_" + request.getTableName(), "VERIFY", command, 1800, request, triggeredBy, null);
    }

    public List<TableMetadata> listTables() {
        return metadataService.listTables();
    }

    private Optional<TableMetadata> findTable(TableOpsRequest request) {
        return metadataService.findTable(request.getDatabaseName(), request.getTableName());
    }

    private Object submit(String taskName,
                          String taskType,
                          String command,
                          int timeoutSeconds,
                          TableOpsRequest request,
                          String triggeredBy,
                          TaskExecutionService.CompletionHandler completionHandler) {
        if (isDryRun(request)) {
            String output = "DRY RUN\n\n" + command;
            taskExecutionRepository.save(taskName, taskType, command, 0, output, 0L);
            return new CommandResult(0, output);
        }
        return taskExecutionService.submitShell(
                taskName, taskType, command, timeoutSeconds, triggeredBy, null, completionHandler);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean isDryRun(TableOpsRequest request) {
        return request != null && Boolean.TRUE.equals(request.getDryRun());
    }

    private String shell(String value) {
        return value == null ? "" : value.replace("'", "'\"'\"'");
    }

    private String q(String value) {
        return "'" + shell(value) + "'";
    }
}
