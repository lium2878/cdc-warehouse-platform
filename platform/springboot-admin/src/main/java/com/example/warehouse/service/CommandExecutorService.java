package com.example.warehouse.service;

import com.example.warehouse.model.CommandResult;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CommandExecutorService {
    private static final int OUTPUT_EXCERPT_BYTES = 16 * 1024;

    private final String projectRoot;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    private final AtomicLong temporaryExecutionIds = new AtomicLong(-1);

    public CommandExecutorService(@Value("${warehouse.project-root:../..}") String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public File getProjectRoot() {
        return new File(projectRoot);
    }

    /** Runs short infrastructure probes synchronously. Long jobs use TaskExecutionService. */
    public CommandResult run(List<String> command, long timeoutSeconds) {
        File directory = new File(projectRoot, "data/ops/command-executions");
        try {
            Files.createDirectories(directory.toPath());
            File logFile = File.createTempFile("command-", ".log", directory);
            CommandResult result = runManaged(
                    temporaryExecutionIds.getAndDecrement(), command, timeoutSeconds, logFile, null);
            Files.deleteIfExists(logFile.toPath());
            return result;
        } catch (IOException ex) {
            return new CommandResult(1, ex.getMessage());
        }
    }

    public CommandResult runManaged(long executionId,
                                    List<String> command,
                                    long timeoutSeconds,
                                    File logFile,
                                    Runnable heartbeat) {
        long startedAt = System.nanoTime();
        Process process = null;
        try {
            File parent = logFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(projectRoot));
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(logFile));
            process = builder.start();
            runningProcesses.put(executionId, process);

            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                if (heartbeat != null) {
                    heartbeat.run();
                }
                long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);
                if (elapsedSeconds >= timeoutSeconds) {
                    terminate(process);
                    return new CommandResult(124, tail(logFile));
                }
            }
            return new CommandResult(process.exitValue(), tail(logFile));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            terminate(process);
            return new CommandResult(130, tail(logFile));
        } catch (Exception ex) {
            return new CommandResult(1, ex.getMessage() + "\n" + tail(logFile));
        } finally {
            runningProcesses.remove(executionId);
        }
    }

    public boolean cancel(long executionId) {
        Process process = runningProcesses.get(executionId);
        if (process == null) {
            return false;
        }
        terminate(process);
        return true;
    }

    @PreDestroy
    public void shutdown() {
        for (Process process : runningProcesses.values()) {
            terminate(process);
        }
        runningProcesses.clear();
    }

    private void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private String tail(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, input.length() - OUTPUT_EXCERPT_BYTES);
            input.seek(start);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.readFully(bytes);
            String value = new String(bytes, StandardCharsets.UTF_8);
            return start == 0 ? value : "[output truncated]\n" + value;
        } catch (IOException ex) {
            return "unable to read task log: " + ex.getMessage();
        }
    }
}
