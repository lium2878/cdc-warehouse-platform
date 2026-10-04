package com.example.warehouse.service;

import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.TaskExecutionRepository;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    private static final int LOG_READ_LIMIT = 256 * 1024;

    private final TaskExecutionRepository repository;
    private final CommandExecutorService commandExecutorService;
    private final Executor executor;
    private final Set<Long> localExecutions = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final Set<Long> cancellationRequests = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    public TaskExecutionService(TaskExecutionRepository repository,
                                CommandExecutorService commandExecutorService,
                                @Qualifier("warehouseTaskExecutor") Executor executor) {
        this.repository = repository;
        this.commandExecutorService = commandExecutorService;
        this.executor = executor;
    }

    @PostConstruct
    public void recoverStaleExecutions() {
        try {
            repository.recoverStale(Instant.now().minusSeconds(120));
        } catch (DataAccessException ignored) {
            // Dev profile may run with file fallback while MySQL is unavailable.
        }
    }

    public TaskExecution submitShell(String taskName,
                                     String taskType,
                                     String command,
                                     int timeoutSeconds,
                                     String triggeredBy,
                                     Long parentExecutionId) {
        return submitShell(taskName, taskType, command, timeoutSeconds, triggeredBy, parentExecutionId, null);
    }

    public TaskExecution submitShell(String taskName,
                                     String taskType,
                                     String command,
                                     int timeoutSeconds,
                                     String triggeredBy,
                                     Long parentExecutionId,
                                     CompletionHandler completionHandler) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        String lockKey = taskName;
        final long executionId;
        try {
            executionId = repository.createPending(
                    taskName,
                    taskType,
                    command,
                    "{}",
                    timeoutSeconds,
                    valueOrDefault(triggeredBy, "system"),
                    parentExecutionId,
                    lockKey);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("task already pending or running: " + taskName);
        }

        localExecutions.add(executionId);
        try {
            executor.execute(() -> execute(executionId, command, timeoutSeconds, completionHandler));
        } catch (RuntimeException ex) {
            localExecutions.remove(executionId);
            repository.finish(executionId, "FAILED", 1, "", 0L, "task executor rejected submission");
            throw ex;
        }
        return require(executionId);
    }

    public TaskExecution rerun(long executionId, String triggeredBy) {
        TaskExecution previous = repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("task execution not found: " + executionId));
        int timeout = previous.getTimeoutSeconds() == null ? 600 : previous.getTimeoutSeconds();
        return submitShell(
                previous.getTaskName(),
                previous.getTaskType(),
                previous.getCommand(),
                timeout,
                triggeredBy,
                previous.getId());
    }

    public TaskExecution cancel(long executionId) {
        TaskExecution execution = require(executionId);
        if ("PENDING".equals(execution.getStatus())) {
            cancellationRequests.add(executionId);
            if (repository.cancelPending(executionId)) {
                return require(executionId);
            }
            execution = require(executionId);
        }
        if ("RUNNING".equals(execution.getStatus())) {
            cancellationRequests.add(executionId);
            commandExecutorService.cancel(executionId);
            return require(executionId);
        }
        throw new IllegalStateException("task is already finished: " + execution.getStatus());
    }

    public String readLog(long executionId) {
        TaskExecution execution = require(executionId);
        if (execution.getLogPath() == null || execution.getLogPath().trim().isEmpty()) {
            return execution.getOutputExcerpt() == null ? "" : execution.getOutputExcerpt();
        }
        try {
            File root = commandExecutorService.getProjectRoot().getCanonicalFile();
            File logFile = new File(root, execution.getLogPath()).getCanonicalFile();
            File allowedRoot = new File(root, "data/task-executions").getCanonicalFile();
            if (!logFile.toPath().startsWith(allowedRoot.toPath())) {
                throw new IllegalStateException("invalid task log path");
            }
            return readTail(logFile);
        } catch (IOException ex) {
            throw new IllegalStateException("unable to read task log", ex);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeatLocalExecutions() {
        for (Long id : localExecutions) {
            try {
                repository.heartbeat(id);
            } catch (DataAccessException ignored) {
                // Next heartbeat retries after a transient database failure.
            }
        }
    }

    private void execute(long executionId,
                         String command,
                         int timeoutSeconds,
                         CompletionHandler completionHandler) {
        long startedAt = System.currentTimeMillis();
        String relativeLogPath = "data/task-executions/" + LocalDate.now() + "/" + executionId + ".log";
        try {
            if (!repository.markRunning(executionId, relativeLogPath)) {
                return;
            }
            if (cancellationRequests.contains(executionId)) {
                repository.finish(executionId, "CANCELLED", 130, "", 0L, "cancelled before process start");
                return;
            }
            File logFile = new File(commandExecutorService.getProjectRoot(), relativeLogPath);
            CommandResult result = commandExecutorService.runManaged(
                    executionId,
                    Arrays.asList("bash", "-lc", command),
                    timeoutSeconds,
                    logFile,
                    () -> {
                        repository.heartbeat(executionId);
                        if (cancellationRequests.contains(executionId)) {
                            commandExecutorService.cancel(executionId);
                        }
                    });
            long durationMs = System.currentTimeMillis() - startedAt;
            if (cancellationRequests.remove(executionId)) {
                repository.finish(executionId, "CANCELLED", 130, result.getOutput(), durationMs, "cancelled by user");
            } else if (result.getExitCode() == 124) {
                repository.finish(executionId, "TIMEOUT", 124, result.getOutput(), durationMs, "task timed out");
            } else if (result.getExitCode() == 0) {
                repository.finish(executionId, "SUCCESS", 0, result.getOutput(), durationMs, null);
            } else {
                repository.finish(executionId, "FAILED", result.getExitCode(), result.getOutput(), durationMs, "command failed");
            }
            notifyCompletion(executionId, completionHandler);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            repository.finish(executionId, "FAILED", 1, "", durationMs, ex.getMessage());
            notifyCompletion(executionId, completionHandler);
        } finally {
            localExecutions.remove(executionId);
            cancellationRequests.remove(executionId);
        }
    }

    private TaskExecution require(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("task execution not found: " + id));
    }

    private String readTail(File file) throws IOException {
        if (!file.exists()) {
            return "";
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, input.length() - LOG_READ_LIMIT);
            input.seek(start);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.readFully(bytes);
            String value = new String(bytes, StandardCharsets.UTF_8);
            return start == 0 ? value : "[log truncated]\n" + value;
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void notifyCompletion(long executionId, CompletionHandler completionHandler) {
        if (completionHandler == null) {
            return;
        }
        try {
            completionHandler.onComplete(require(executionId));
        } catch (RuntimeException ignored) {
            // Business-side result persistence must not change the task terminal state.
        }
    }

    @FunctionalInterface
    public interface CompletionHandler {
        void onComplete(TaskExecution execution);
    }
}
