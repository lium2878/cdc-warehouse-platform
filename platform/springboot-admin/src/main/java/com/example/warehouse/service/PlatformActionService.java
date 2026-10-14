package com.example.warehouse.service;

import com.example.warehouse.model.ActionRequest;
import com.example.warehouse.model.TaskExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PlatformActionService {
    private final CommandExecutorService commandExecutorService;
    private final TaskExecutionService taskExecutionService;

    public PlatformActionService(CommandExecutorService commandExecutorService,
                                 TaskExecutionService taskExecutionService) {
        this.commandExecutorService = commandExecutorService;
        this.taskExecutionService = taskExecutionService;
    }

    public Object run(String action, ActionRequest request, String triggeredBy) {
        if ("refresh-ops".equals(action)) {
            return commandExecutorService.run(Arrays.asList("bash", "scripts/refresh_ops_snapshot.sh"), 60);
        }
        if ("test-alert".equals(action)) {
            return commandExecutorService.run(Arrays.asList("python3", "scripts/send_test_alert.py"), 60);
        }
        if ("daily-merge".equals(action)) {
            return submit("daily_merge", "MERGE", jobCommand("daily-merge", request), 1800, triggeredBy);
        }
        if ("monitor-suite".equals(action)) {
            return submit("monitor_suite", "MONITOR", jobCommand("monitors", request), 1800, triggeredBy);
        }
        if ("ds-publish".equals(action)) {
            String mode = request == null || !notBlank(request.getMode()) ? "--audit" : request.getMode().trim();
            if (!"--audit".equals(mode) && !"--dry-run".equals(mode) && !"--live".equals(mode)) {
                throw new IllegalArgumentException("invalid mode: " + mode);
            }
            return submit("ds_publish", "PUBLISH",
                    Arrays.asList("python3", "scripts/publish_dolphinscheduler.py", mode), 300, triggeredBy);
        }
        if ("realtime-kafka-kudu-once".equals(action)) {
            return submit("realtime_kafka_kudu_once", "REALTIME", Arrays.asList(
                    "python3", "scripts/spark_streaming_kafka_to_kudu_once.py", "--bootstrap-objects"),
                    1800, triggeredBy);
        }
        if ("verify-e2e-local".equals(action)) {
            return submit("verify_e2e_local", "VERIFY", verifyCommand("local", request), 1800, triggeredBy);
        }
        if ("verify-e2e-server".equals(action)) {
            return submit("verify_e2e_server", "VERIFY", verifyCommand("server", request), 1800, triggeredBy);
        }
        throw new IllegalArgumentException("unknown action: " + action);
    }

    private List<String> jobCommand(String job, ActionRequest request) {
        List<String> command = new ArrayList<>(Arrays.asList("bash", "deploy/run_job.sh", job));
        if (request != null && notBlank(request.getBizDt())) {
            command.add(request.getBizDt().trim());
        }
        return command;
    }

    private List<String> verifyCommand(String mode, ActionRequest request) {
        List<String> command = new ArrayList<>(Arrays.asList(
                "bash", "scripts/verify_end_to_end.sh", "--mode", mode));
        if (request != null && notBlank(request.getBizDt())) {
            command.add("--biz-dt");
            command.add(request.getBizDt().trim());
        }
        return command;
    }

    private TaskExecution submit(String taskName,
                                 String taskType,
                                 List<String> command,
                                 int timeoutSeconds,
                                 String triggeredBy) {
        return taskExecutionService.submitShell(
                taskName, taskType, shellCommand(command), timeoutSeconds, triggeredBy, null);
    }

    private String shellCommand(List<String> command) {
        return command.stream().map(this::q).collect(Collectors.joining(" "));
    }

    private String q(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
