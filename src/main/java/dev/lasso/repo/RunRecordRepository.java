package dev.lasso.repo;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class RunRecordRepository {

    private final JdbcTemplate jdbc;

    public RunRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS run_record (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  created_at TEXT NOT NULL,
                  action TEXT NOT NULL,
                  entry_index INTEGER NOT NULL,
                  fairness_enabled INTEGER NOT NULL,
                  reduction_kind TEXT,
                  verdict TEXT NOT NULL,
                  violation_reproduced INTEGER NOT NULL,
                  payload_json TEXT NOT NULL,
                  source TEXT NOT NULL
                )
                """);
    }

    private static final RowMapper<RunRecord> MAPPER = (rs, n) -> new RunRecord(
            rs.getLong("id"),
            rs.getString("created_at"),
            rs.getString("action"),
            rs.getInt("entry_index"),
            rs.getInt("fairness_enabled") == 1,
            rs.getString("reduction_kind"),
            rs.getString("verdict"),
            rs.getInt("violation_reproduced") == 1,
            rs.getString("payload_json"),
            rs.getString("source"));

    public RunRecord insert(String createdAt, String action, int entryIndex, boolean fairnessEnabled,
                            String reductionKind, String verdict, boolean violationReproduced,
                            String payloadJson, String source) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO run_record(created_at, action, entry_index, fairness_enabled, "
                            + "reduction_kind, verdict, violation_reproduced, payload_json, source) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, createdAt);
            ps.setString(2, action);
            ps.setInt(3, entryIndex);
            ps.setInt(4, fairnessEnabled ? 1 : 0);
            ps.setString(5, reductionKind);
            ps.setString(6, verdict);
            ps.setInt(7, violationReproduced ? 1 : 0);
            ps.setString(8, payloadJson);
            ps.setString(9, source);
            return ps;
        }, holder);
        Number key = holder.getKey();
        return new RunRecord(key == null ? null : key.longValue(), createdAt, action, entryIndex,
                fairnessEnabled, reductionKind, verdict, violationReproduced, payloadJson, source);
    }

    public List<RunRecord> findAll() {
        return jdbc.query("SELECT * FROM run_record ORDER BY id ASC", MAPPER);
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM run_record", Long.class);
        return value == null ? 0 : value;
    }

    public void clear() {
        jdbc.execute("DELETE FROM run_record");
    }
}
