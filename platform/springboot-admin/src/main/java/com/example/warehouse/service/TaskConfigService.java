package com.example.warehouse.service;

import com.example.warehouse.config.WarehouseProperties;
import com.example.warehouse.model.SparkTaskConfig;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.TaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskConfigService {
    private static final Logger log = LoggerFactory.getLogger(TaskConfigService.class);
    private static final String FALLBACK_PATH = "data/platform/task_configs.json";

    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final CommandExecutorService commandExecutorService;
    private final WarehouseProperties warehouseProperties;
    private final TaskExecutionService taskExecutionService;

    public TaskConfigService(TaskRepository taskRepository,
                             ObjectMapper objectMapper,
                             CommandExecutorService commandExecutorService,
                             WarehouseProperties warehouseProperties,
                             TaskExecutionService taskExecutionService) {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.commandExecutorService = commandExecutorService;
        this.warehouseProperties = warehouseProperties;
        this.taskExecutionService = taskExecutionService;
    }

    public List<SparkTaskConfig> listTasks() {
        List<SparkTaskConfig> tasks = taskRepository.findEnabledTasks();
        if (!tasks.isEmpty()) {
            return tasks;
        }
        if (!warehouseProperties.getMysql().isFallbackDemoData()) {
            throw new IllegalStateException("No task config from MySQL and fallback is disabled");
        }
        log.info("MySQL task config unavailable or empty, reading local fallback: {}", FALLBACK_PATH);
        return loadFromJsonFile();
    }

    private List<SparkTaskConfig> loadFromJsonFile() {
        File file = new File(commandExecutorService.getProjectRoot(), FALLBACK_PATH);
        if (!file.exists()) {
            log.warn("Fallback file not found: {}", FALLBACK_PATH);
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(file, new TypeReference<List<SparkTaskConfig>>() {});
        } catch (IOException e) {
            log.error("Failed to read fallback tasks: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void saveTask(SparkTaskConfig task) {
        taskRepository.upsert(task);
    }

    public TaskExecution runTask(String taskName, String triggeredBy) {
        return listTasks().stream()
                .filter(task -> taskName.equals(task.getTaskName()))
                .findFirst()
                .map(task -> taskExecutionService.submitShell(
                        task.getTaskName(), task.getTaskType(), task.getCommand(), 600, triggeredBy, null))
                .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskName));
    }

    public TaskExecution rerunExecution(long executionId, String triggeredBy) {
        return taskExecutionService.rerun(executionId, triggeredBy);
    }
}
