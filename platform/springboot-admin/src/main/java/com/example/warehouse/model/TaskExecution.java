package com.example.warehouse.model;

public class TaskExecution {
    private Long id;
    private String taskName;
    private String taskType;
    private String command;
    private String status;
    private Integer exitCode;
    private String outputExcerpt;
    private Long durationMs;
    private String parametersJson;
    private String logPath;
    private Long processId;
    private Integer timeoutSeconds;
    private String triggeredBy;
    private String startedAt;
    private String finishedAt;
    private String heartbeatAt;
    private Long parentExecutionId;
    private String errorMessage;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getOutputExcerpt() { return outputExcerpt; }
    public void setOutputExcerpt(String outputExcerpt) { this.outputExcerpt = outputExcerpt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getLogPath() { return logPath; }
    public void setLogPath(String logPath) { this.logPath = logPath; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public String getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(String heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Long getParentExecutionId() { return parentExecutionId; }
    public void setParentExecutionId(Long parentExecutionId) { this.parentExecutionId = parentExecutionId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
