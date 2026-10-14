package com.example.warehouse.service;

import com.example.warehouse.model.OnboardRequest;
import com.example.warehouse.model.SparkTaskConfig;
import com.example.warehouse.model.TableMetadata;
import com.example.warehouse.model.TaskExecution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final CommandExecutorService commandExecutorService;
    private final TaskExecutionService taskExecutionService;
    private final OnboardingRegistrationService registrationService;
    private final ObjectMapper objectMapper;

    public OnboardingService(CommandExecutorService commandExecutorService,
                             TaskExecutionService taskExecutionService,
                             OnboardingRegistrationService registrationService,
                             ObjectMapper objectMapper) {
        this.commandExecutorService = commandExecutorService;
        this.taskExecutionService = taskExecutionService;
        this.registrationService = registrationService;
        this.objectMapper = objectMapper;
    }

    public List<String> buildPlan(OnboardRequest request) {
        String code = value(request.getDatabaseName()) + "." + value(request.getTableName());
        return Arrays.asList(
                "Pull DBA metadata for " + code,
                "Generate metadata/tables/" + code + ".json",
                "Generate ods_binlog DDL",
                "Generate ods snapshot DDL",
                "Generate SparkSQL merge SQL",
                "Generate DolphinScheduler merge task",
                "Bootstrap MySQL full data into ods_binlog",
                "Replace ODS initial snapshot from MySQL full data"
        );
    }

    public TaskExecution execute(OnboardRequest request, String triggeredBy) {
        validate(request);
        TableMetadata table = buildMetadata(request);
        SparkTaskConfig task = buildTask(request);
        String command = shellCommand(Arrays.asList(
                "python3",
                "scripts/onboard_table.py",
                request.getDbaMetadataPath().trim(),
                request.getPrimaryKeys().trim(),
                request.getVersionColumn().trim(),
                request.getPartitionColumn().trim(),
                "--bootstrap",
                "--merge-bootstrap"
        ));
        return taskExecutionService.submitShellWithSuccessHandler(
                "onboarding_" + request.getDatabaseName().trim() + "_" + request.getTableName().trim(),
                "ONBOARDING",
                command,
                1800,
                triggeredBy,
                null,
                () -> registrationService.register(table, task));
    }

    private TableMetadata buildMetadata(OnboardRequest request) {
        TableMetadata table = new TableMetadata();
        String database = request.getDatabaseName().trim();
        String sourceTable = request.getTableName().trim();
        table.setDatabaseName(database);
        table.setTableName(sourceTable);
        table.setOdsBinlogTable("ods_binlog_" + database + "_" + sourceTable + "_di");
        table.setOdsTable("ods_" + database + "_" + sourceTable + "_dic");
        table.setPrimaryKeys(Arrays.stream(request.getPrimaryKeys().split(","))
                .map(String::trim).collect(Collectors.toList()));
        table.setVersionColumn(request.getVersionColumn().trim());
        table.setPartitionColumn(request.getPartitionColumn().trim());
        table.setColumnsJson(readColumnsJson(request));
        return table;
    }

    private SparkTaskConfig buildTask(OnboardRequest request) {
        SparkTaskConfig task = new SparkTaskConfig();
        String odsTable = "ods_" + request.getDatabaseName().trim() + "_" + request.getTableName().trim() + "_dic";
        task.setTaskName("merge_" + odsTable);
        task.setTaskType("SparkSQL");
        task.setCommand("spark-sql -f warehouse/sql/ods/merge/merge_" + odsTable + ".sql");
        task.setSchedule("0 30 2 * * ?");
        return task;
    }

    private String readColumnsJson(OnboardRequest request) {
        try {
            File root = commandExecutorService.getProjectRoot().getCanonicalFile();
            File dbaFile = new File(root, request.getDbaMetadataPath().trim()).getCanonicalFile();
            if (!dbaFile.toPath().startsWith(root.toPath())) {
                throw new IllegalArgumentException("DBA metadata path must stay inside project root");
            }
            if (!dbaFile.isFile()) {
                throw new IllegalArgumentException("DBA metadata file not found: " + request.getDbaMetadataPath());
            }
            JsonNode columns = objectMapper.readTree(dbaFile).get("columns");
            if (columns == null || !columns.isArray() || columns.size() == 0) {
                throw new IllegalArgumentException("DBA metadata columns must be a non-empty array");
            }
            return objectMapper.writeValueAsString(columns);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid DBA metadata: " + ex.getMessage(), ex);
        }
    }

    private void validate(OnboardRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("onboarding request is required");
        }
        requireIdentifier("databaseName", request.getDatabaseName());
        requireIdentifier("tableName", request.getTableName());
        requireIdentifier("versionColumn", request.getVersionColumn());
        requireIdentifier("partitionColumn", request.getPartitionColumn());
        if (value(request.getDbaMetadataPath()).isEmpty()) {
            throw new IllegalArgumentException("dbaMetadataPath is required");
        }
        String[] primaryKeys = value(request.getPrimaryKeys()).split(",");
        if (primaryKeys.length == 0) {
            throw new IllegalArgumentException("primaryKeys is required");
        }
        for (String primaryKey : primaryKeys) {
            requireIdentifier("primaryKeys", primaryKey);
        }
    }

    private void requireIdentifier(String field, String input) {
        if (!IDENTIFIER.matcher(value(input)).matches()) {
            throw new IllegalArgumentException("invalid " + field + ": " + value(input));
        }
    }

    private String shellCommand(List<String> command) {
        return command.stream().map(this::q).collect(Collectors.joining(" "));
    }

    private String q(String input) {
        return "'" + input.replace("'", "'\"'\"'") + "'";
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
