package com.example.warehouse.controller;

import com.example.warehouse.model.MergeTaskStatus;
import com.example.warehouse.model.SparkTaskConfig;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.TaskExecutionRepository;
import com.example.warehouse.service.CommandExecutorService;
import com.example.warehouse.service.MergeTaskStatusService;
import com.example.warehouse.service.TaskConfigService;
import com.example.warehouse.service.TaskExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Controller
@Tag(name = "Tasks", description = "Spark task configuration")
public class TaskController {
    private final TaskConfigService taskConfigService;
    private final TaskExecutionRepository taskExecutionRepository;
    private final MergeTaskStatusService mergeTaskStatusService;
    private final CommandExecutorService commandExecutorService;
    private final TaskExecutionService taskExecutionService;

    public TaskController(TaskConfigService taskConfigService,
                          TaskExecutionRepository taskExecutionRepository,
                          MergeTaskStatusService mergeTaskStatusService,
                          CommandExecutorService commandExecutorService,
                          TaskExecutionService taskExecutionService) {
        this.taskConfigService = taskConfigService;
        this.taskExecutionRepository = taskExecutionRepository;
        this.mergeTaskStatusService = mergeTaskStatusService;
        this.commandExecutorService = commandExecutorService;
        this.taskExecutionService = taskExecutionService;
    }

    @GetMapping("/tasks")
    @Operation(summary = "List task configurations")
    public String tasks(Model model) {
        model.addAttribute("tasks", taskConfigService.listTasks());
        model.addAttribute("executions", taskExecutionRepository.findLatest(20));
        model.addAttribute("mergeStatuses", mergeTaskStatusService.latest());
        SparkTaskConfig task = new SparkTaskConfig();
        task.setTaskName("ods_merge");
        task.setTaskType("SparkSQL");
        task.setCommand("spark-sql -f warehouse/sql/ods/merge/merge_ods_basiccomment_avatar_commentbatchsource_dic.sql");
        task.setSchedule("0 30 2 * * ?");
        model.addAttribute("task", task);
        return "tasks";
    }

    @PostMapping("/tasks")
    @Operation(summary = "Save Spark task configuration")
    public String saveTask(@ModelAttribute SparkTaskConfig task, Model model) {
        taskConfigService.saveTask(task);
        model.addAttribute("tasks", taskConfigService.listTasks());
        model.addAttribute("executions", taskExecutionRepository.findLatest(20));
        model.addAttribute("mergeStatuses", mergeTaskStatusService.latest());
        model.addAttribute("task", task);
        model.addAttribute("message", "task saved");
        return "tasks";
    }

    @PostMapping("/api/tasks/run/{taskName}")
    @ResponseBody
    @Operation(summary = "Run one configured task")
    public ResponseEntity<?> runTask(@PathVariable String taskName, Principal principal) {
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(taskConfigService.runTask(taskName, operator(principal)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
        }
    }

    @GetMapping("/api/tasks/executions/{id}")
    @ResponseBody
    @Operation(summary = "Get one task execution detail")
    public TaskExecution taskExecution(@PathVariable long id) {
        return taskExecutionRepository.findById(id).orElseGet(TaskExecution::new);
    }

    @PostMapping("/api/tasks/executions/{id}/rerun")
    @ResponseBody
    @Operation(summary = "Rerun one task execution command")
    public ResponseEntity<?> rerunExecution(@PathVariable long id, Principal principal) {
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(taskConfigService.rerunExecution(id, operator(principal)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
        }
    }

    @PostMapping("/api/tasks/executions/{id}/cancel")
    @ResponseBody
    @Operation(summary = "Cancel one pending or running task execution")
    public ResponseEntity<?> cancelExecution(@PathVariable long id) {
        try {
            return ResponseEntity.ok(taskExecutionService.cancel(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
        }
    }

    @GetMapping(value = "/api/tasks/executions/{id}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    @Operation(summary = "Read one task execution log")
    public String executionLog(@PathVariable long id) {
        return taskExecutionService.readLog(id);
    }

    @GetMapping("/api/tasks/executions/{id}/context")
    @ResponseBody
    @Operation(summary = "Get task failure context logs")
    public Map<String, String> executionContext(@PathVariable long id) {
        Map<String, String> context = new LinkedHashMap<>();
        taskExecutionRepository.findById(id).ifPresent(item -> {
            context.put("execution", "id=" + item.getId()
                    + "\ntask=" + item.getTaskName()
                    + "\nstatus=" + item.getStatus()
                    + "\nexitCode=" + item.getExitCode()
                    + "\ncommand=" + item.getCommand()
                    + "\n\n" + nullToEmpty(item.getOutputExcerpt()));
        });
        context.put("spark_sql_merge.log", readOps("spark_sql_merge.log"));
        context.put("verify_end_to_end.log", readOps("verify_end_to_end.log"));
        context.put("spark_streaming.log", readOps("spark_streaming.log"));
        context.put("maxwell.log", readOps("maxwell.log"));
        context.put("admin.log", readOps("admin.log"));
        return context;
    }

    @GetMapping("/api/tasks/executions")
    @ResponseBody
    @Operation(summary = "List latest task executions")
    public List<TaskExecution> taskExecutions() {
        return taskExecutionRepository.findLatest(50);
    }

    @GetMapping("/api/tasks/merge-status")
    @ResponseBody
    @Operation(summary = "List latest ODS merge task status")
    public List<MergeTaskStatus> mergeStatuses() {
        return mergeTaskStatusService.latest();
    }

    private String readOps(String name) {
        File file = new File(commandExecutorService.getProjectRoot(), "data/ops/" + name);
        if (!file.exists()) {
            return "not found: data/ops/" + name;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R");
            int start = Math.max(0, lines.length - 120);
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                builder.append(lines[i]).append('\n');
            }
            return builder.toString();
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    private Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
