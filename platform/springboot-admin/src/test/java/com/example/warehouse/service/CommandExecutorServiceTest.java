package com.example.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.warehouse.model.CommandResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandExecutorServiceTest {
    @TempDir
    Path projectRoot;

    @Test
    void capturesCommandOutput() throws Exception {
        CommandExecutorService service = new CommandExecutorService(projectRoot.toString());
        File log = projectRoot.resolve("success.log").toFile();

        CommandResult result = service.runManaged(
                1L, Arrays.asList("bash", "-lc", "printf 'hello'"), 5, log, null);

        assertEquals(0, result.getExitCode());
        assertEquals("hello", result.getOutput());
        assertEquals("hello", new String(Files.readAllBytes(log.toPath()), "UTF-8"));
    }

    @Test
    void enforcesTimeoutWithoutWaitingForOutputEof() {
        CommandExecutorService service = new CommandExecutorService(projectRoot.toString());
        long startedAt = System.currentTimeMillis();

        CommandResult result = service.runManaged(
                2L,
                Arrays.asList("bash", "-lc", "sleep 5"),
                1,
                projectRoot.resolve("timeout.log").toFile(),
                null);

        assertEquals(124, result.getExitCode());
        assertTrue(System.currentTimeMillis() - startedAt < 4000);
    }

    @Test
    void cancelsRunningCommand() throws Exception {
        CommandExecutorService service = new CommandExecutorService(projectRoot.toString());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<CommandResult> result = worker.submit(() -> service.runManaged(
                    3L,
                    Arrays.asList("bash", "-lc", "sleep 10"),
                    30,
                    projectRoot.resolve("cancel.log").toFile(),
                    null));
            long deadline = System.currentTimeMillis() + 3000;
            boolean cancelled = false;
            while (!cancelled && System.currentTimeMillis() < deadline) {
                cancelled = service.cancel(3L);
                if (!cancelled) {
                    Thread.sleep(25);
                }
            }

            assertTrue(cancelled);
            assertTrue(result.get(3, TimeUnit.SECONDS).getExitCode() != 0);
        } finally {
            worker.shutdownNow();
        }
    }
}
