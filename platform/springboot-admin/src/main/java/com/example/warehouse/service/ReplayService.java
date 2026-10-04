package com.example.warehouse.service;

import com.example.warehouse.model.ReplayRecord;
import com.example.warehouse.model.ReplayRequest;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.ReplayRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private final ReplayRepository replayRepository;
    private final TaskExecutionService taskExecutionService;

    public ReplayService(ReplayRepository replayRepository,
                         TaskExecutionService taskExecutionService) {
        this.replayRepository = replayRepository;
        this.taskExecutionService = taskExecutionService;
    }

    public String buildBootstrapCommand(ReplayRequest request) {
        validate(request);
        return "python3 scripts/bootstrap_mysql_table.py metadata/tables/"
                + request.getDatabaseName() + "." + request.getTableName()
                + ".json --replace-binlog --replace-ods";
    }

    public TaskExecution execute(ReplayRequest request, String triggeredBy) {
        String command = buildBootstrapCommand(request);
        long recordId = replayRepository.save(request, command);
        try {
            TaskExecution execution = taskExecutionService.submitShell(
                    "replay_" + request.getDatabaseName() + "_" + request.getTableName(),
                    "REPLAY",
                    command,
                    1800,
                    triggeredBy,
                    null);
            replayRepository.attachExecution(recordId, execution.getId());
            return execution;
        } catch (RuntimeException ex) {
            replayRepository.updateStatus(recordId, "REJECTED");
            throw ex;
        }
    }

    public List<ReplayRecord> latest() {
        return replayRepository.findLatest(30);
    }

    private void validate(ReplayRequest request) {
        if (request == null
                || !IDENTIFIER.matcher(value(request.getDatabaseName())).matches()
                || !IDENTIFIER.matcher(value(request.getTableName())).matches()) {
            throw new IllegalArgumentException("invalid database or table name");
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
