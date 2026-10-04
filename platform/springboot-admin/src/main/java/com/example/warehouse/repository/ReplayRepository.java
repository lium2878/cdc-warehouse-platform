package com.example.warehouse.repository;

import com.example.warehouse.model.ReplayRequest;
import com.example.warehouse.model.ReplayRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReplayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureExecutionColumn() {
        try {
            jdbcTemplate.execute("alter table replay_record add column execution_id bigint");
        } catch (DataAccessException ignored) {
            // Existing columns and unavailable dev databases are both handled by callers.
        }
        try {
            jdbcTemplate.execute("create index idx_replay_execution on replay_record(execution_id)");
        } catch (DataAccessException ignored) {
        }
    }

    public long save(ReplayRequest request, String command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into replay_record (source_database, source_table, start_time, end_time, command, status) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, request.getDatabaseName());
            statement.setString(2, request.getTableName());
            statement.setString(3, empty(request.getStartTime()));
            statement.setString(4, empty(request.getEndTime()));
            statement.setString(5, command);
            statement.setString(6, "CREATED");
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    public void attachExecution(long id, long executionId) {
        jdbcTemplate.update(
                "update replay_record set execution_id=?, status='PENDING' where id=?",
                executionId, id);
    }

    public void updateStatus(long id, String status) {
        if (id <= 0L) {
            return;
        }
        try {
            jdbcTemplate.update("update replay_record set status = ? where id = ?", status, id);
        } catch (DataAccessException ignored) {
        }
    }

    public List<ReplayRecord> findLatest(int limit) {
        try {
            return jdbcTemplate.query(
                    "select r.id, r.source_database, r.source_table, r.start_time, r.end_time, r.command, "
                            + "coalesce(e.status, r.status) status, r.execution_id, r.created_at, r.updated_at "
                            + "from replay_record r left join task_execution e on e.id=r.execution_id "
                            + "order by r.id desc limit ?",
                    (rs, rowNum) -> {
                        ReplayRecord record = new ReplayRecord();
                        record.setId(rs.getLong("id"));
                        record.setDatabaseName(rs.getString("source_database"));
                        record.setTableName(rs.getString("source_table"));
                        record.setStartTime(rs.getString("start_time"));
                        record.setEndTime(rs.getString("end_time"));
                        record.setCommand(rs.getString("command"));
                        record.setStatus(rs.getString("status"));
                        record.setExecutionId((Long) rs.getObject("execution_id"));
                        record.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                        record.setUpdatedAt(String.valueOf(rs.getTimestamp("updated_at")));
                        return record;
                    },
                    limit
            );
        } catch (DataAccessException ignored) {
            return Collections.emptyList();
        }
    }

    private String empty(String value) {
        return value == null ? "" : value.trim();
    }
}
