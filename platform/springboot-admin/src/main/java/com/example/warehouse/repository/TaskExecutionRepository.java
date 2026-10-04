package com.example.warehouse.repository;

import com.example.warehouse.model.TaskExecution;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TaskExecutionRepository {
    private final JdbcTemplate jdbcTemplate;

    public TaskExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        try {
            jdbcTemplate.execute(
                    "create table if not exists task_execution ("
                            + "id bigint primary key auto_increment,"
                            + "task_name varchar(256) not null,"
                            + "task_type varchar(64) not null,"
                            + "command text not null,"
                            + "status varchar(32) not null,"
                            + "exit_code int,"
                            + "output_excerpt text,"
                            + "duration_ms bigint,"
                            + "parameters_json text,"
                            + "log_path varchar(1024),"
                            + "process_id bigint,"
                            + "timeout_seconds int,"
                            + "triggered_by varchar(128),"
                            + "started_at timestamp null,"
                            + "finished_at timestamp null,"
                            + "heartbeat_at timestamp null,"
                            + "parent_execution_id bigint,"
                            + "error_message text,"
                            + "created_at timestamp not null default current_timestamp,"
                            + "key idx_task_execution_created_at (created_at),"
                            + "key idx_task_execution_name (task_name)"
                            + ")"
            );
            ensureColumn("parameters_json", "text");
            ensureColumn("log_path", "varchar(1024)");
            ensureColumn("process_id", "bigint");
            ensureColumn("timeout_seconds", "int");
            ensureColumn("triggered_by", "varchar(128)");
            ensureColumn("started_at", "timestamp null");
            ensureColumn("finished_at", "timestamp null");
            ensureColumn("heartbeat_at", "timestamp null");
            ensureColumn("parent_execution_id", "bigint");
            ensureColumn("error_message", "text");
            jdbcTemplate.execute(
                    "create table if not exists task_execution_lock ("
                            + "lock_key varchar(512) primary key,"
                            + "execution_id bigint not null,"
                            + "created_at timestamp not null default current_timestamp,"
                            + "key idx_task_execution_lock_execution (execution_id)"
                            + ")"
            );
        } catch (DataAccessException ignored) {
            // Startup validation reports unavailable MySQL with a clearer message.
        }
    }

    private void ensureColumn(String name, String definition) {
        try {
            jdbcTemplate.execute("alter table task_execution add column " + name + " " + definition);
        } catch (DataAccessException ignored) {
            // Existing columns are expected on subsequent starts.
        }
    }

    @Transactional
    public long createPending(String taskName,
                              String taskType,
                              String command,
                              String parametersJson,
                              int timeoutSeconds,
                              String triggeredBy,
                              Long parentExecutionId,
                              String lockKey) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into task_execution(task_name, task_type, command, status, parameters_json, "
                            + "timeout_seconds, triggered_by, parent_execution_id, heartbeat_at) "
                            + "values(?,?,?,'PENDING',?,?,?,?,current_timestamp)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, taskName);
            statement.setString(2, taskType);
            statement.setString(3, command);
            statement.setString(4, parametersJson);
            statement.setInt(5, timeoutSeconds);
            statement.setString(6, triggeredBy);
            if (parentExecutionId == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, parentExecutionId);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("task execution id was not generated");
        }
        long executionId = key.longValue();
        jdbcTemplate.update(
                "insert into task_execution_lock(lock_key, execution_id) values(?,?)",
                lockKey, executionId);
        return executionId;
    }

    public boolean markRunning(long id, String logPath) {
        return jdbcTemplate.update(
                "update task_execution set status='RUNNING', log_path=?, started_at=current_timestamp, "
                        + "heartbeat_at=current_timestamp where id=? and status='PENDING'",
                logPath, id) > 0;
    }

    public void heartbeat(long id) {
        jdbcTemplate.update(
                "update task_execution set heartbeat_at=current_timestamp where id=? and status in ('PENDING','RUNNING')",
                id);
    }

    @Transactional
    public void finish(long id, String status, Integer exitCode, String output, long durationMs, String errorMessage) {
        jdbcTemplate.update(
                "update task_execution set status=?, exit_code=?, output_excerpt=?, duration_ms=?, error_message=?, "
                        + "finished_at=current_timestamp, heartbeat_at=current_timestamp where id=?",
                status, exitCode, excerpt(output), durationMs, errorMessage, id);
        releaseLock(id);
    }

    @Transactional
    public boolean cancelPending(long id) {
        int updated = jdbcTemplate.update(
                "update task_execution set status='CANCELLED', exit_code=130, finished_at=current_timestamp, "
                        + "error_message='cancelled before start' where id=? and status='PENDING'",
                id);
        if (updated > 0) {
            releaseLock(id);
        }
        return updated > 0;
    }

    @Transactional
    public int recoverStale(Instant cutoff) {
        int updated = jdbcTemplate.update(
                "update task_execution set status='FAILED', exit_code=1, finished_at=current_timestamp, "
                        + "error_message='worker heartbeat expired' "
                        + "where status in ('PENDING','RUNNING') and heartbeat_at < ?",
                Timestamp.from(cutoff));
        jdbcTemplate.update(
                "delete l from task_execution_lock l left join task_execution e on e.id=l.execution_id "
                        + "where e.id is null or e.status not in ('PENDING','RUNNING')");
        return updated;
    }

    public void save(String taskName, String taskType, String command, int exitCode, String output, long durationMs) {
        String status = exitCode == 0 ? "SUCCESS" : "FAILED";
        try {
            jdbcTemplate.update(
                    "insert into task_execution(task_name, task_type, command, status, exit_code, output_excerpt, "
                            + "duration_ms, started_at, finished_at, heartbeat_at) "
                            + "values(?,?,?,?,?,?,?,current_timestamp,current_timestamp,current_timestamp)",
                    taskName, taskType, command, status, exitCode, excerpt(output), durationMs);
        } catch (DataAccessException ignored) {
        }
    }

    public List<TaskExecution> findLatest(int limit) {
        try {
            return jdbcTemplate.query(
                    "select * from task_execution order by id desc limit ?",
                    (rs, rowNum) -> map(rs),
                    limit);
        } catch (DataAccessException ex) {
            return Collections.emptyList();
        }
    }

    public Optional<TaskExecution> findById(long id) {
        try {
            List<TaskExecution> items = jdbcTemplate.query(
                    "select * from task_execution where id = ?",
                    (rs, rowNum) -> map(rs),
                    id);
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    private void releaseLock(long executionId) {
        jdbcTemplate.update("delete from task_execution_lock where execution_id=?", executionId);
    }

    private String excerpt(String output) {
        String value = output == null ? "" : output;
        return value.length() <= 4000 ? value : value.substring(value.length() - 4000);
    }

    private TaskExecution map(java.sql.ResultSet rs) throws java.sql.SQLException {
        TaskExecution item = new TaskExecution();
        item.setId(rs.getLong("id"));
        item.setTaskName(rs.getString("task_name"));
        item.setTaskType(rs.getString("task_type"));
        item.setCommand(rs.getString("command"));
        item.setStatus(rs.getString("status"));
        item.setExitCode((Integer) rs.getObject("exit_code"));
        item.setOutputExcerpt(rs.getString("output_excerpt"));
        item.setDurationMs((Long) rs.getObject("duration_ms"));
        item.setParametersJson(rs.getString("parameters_json"));
        item.setLogPath(rs.getString("log_path"));
        item.setProcessId((Long) rs.getObject("process_id"));
        item.setTimeoutSeconds((Integer) rs.getObject("timeout_seconds"));
        item.setTriggeredBy(rs.getString("triggered_by"));
        item.setStartedAt(timestamp(rs, "started_at"));
        item.setFinishedAt(timestamp(rs, "finished_at"));
        item.setHeartbeatAt(timestamp(rs, "heartbeat_at"));
        item.setParentExecutionId((Long) rs.getObject("parent_execution_id"));
        item.setErrorMessage(rs.getString("error_message"));
        item.setCreatedAt(timestamp(rs, "created_at"));
        return item;
    }

    private String timestamp(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toString();
    }
}
