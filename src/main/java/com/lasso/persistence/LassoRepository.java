package com.lasso.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.Transition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class LassoRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LassoRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- models

    public List<Model> findAllModels() {
        List<Model> models = new ArrayList<>();
        jdbc.query("SELECT id FROM models ORDER BY id", rs -> {
            String id = rs.getString("id");
            models.add(loadModel(id));
        });
        return models;
    }

    public Model findModel(String modelId) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM models WHERE id = ?", String.class, modelId);
        return ids.isEmpty() ? null : loadModel(ids.get(0));
    }

    private Model loadModel(String modelId) {
        return jdbc.queryForObject(
                "SELECT id, name, description FROM models WHERE id = ?",
                (rs, rowNum) -> new Model(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        findStates(modelId),
                        findTransitions(modelId)),
                modelId);
    }

    private List<ModelState> findStates(String modelId) {
        return jdbc.query(
                "SELECT state_id, ord, variables, accept_sets, fair_sets FROM states "
                        + "WHERE model_id = ? ORDER BY ord",
                (rs, rowNum) -> new ModelState(
                        modelId,
                        rs.getString("state_id"),
                        rs.getInt("ord"),
                        readMap(rs.getString("variables")),
                        readStringList(rs.getString("accept_sets")),
                        readStringList(rs.getString("fair_sets"))),
                modelId);
    }

    private List<Transition> findTransitions(String modelId) {
        return jdbc.query(
                "SELECT id, source, target, guard, description, ord FROM transitions "
                        + "WHERE model_id = ? ORDER BY ord, id",
                (rs, rowNum) -> new Transition(
                        rs.getLong("id"),
                        modelId,
                        rs.getInt("ord"),
                        rs.getString("source"),
                        rs.getString("target"),
                        rs.getString("guard"),
                        rs.getString("description")),
                modelId);
    }

    // ----------------------------------------------------- counterexamples

    public List<Counterexample> findAllCounterexamples() {
        return jdbc.query(
                "SELECT id, model_id, name, property_kind, description FROM counterexamples ORDER BY id",
                (rs, rowNum) -> loadCounterexample(rs.getString("id"), rs.getString("model_id"),
                        rs.getString("name"), rs.getString("property_kind"),
                        rs.getString("description")));
    }

    public Counterexample findCounterexample(String ceId) {
        List<Counterexample> list = jdbc.query(
                "SELECT id, model_id, name, property_kind, description FROM counterexamples WHERE id = ?",
                (rs, rowNum) -> loadCounterexample(rs.getString("id"), rs.getString("model_id"),
                        rs.getString("name"), rs.getString("property_kind"),
                        rs.getString("description")),
                ceId);
        return list.isEmpty() ? null : list.get(0);
    }

    private Counterexample loadCounterexample(String id, String modelId, String name,
                                              String propertyKind, String description) {
        List<String> trace = jdbc.queryForList(
                "SELECT state_id FROM ce_traces WHERE ce_id = ? ORDER BY pos",
                String.class, id);
        List<Counterexample.EntryCandidate> entries = jdbc.query(
                "SELECT state_id, legal, note FROM ce_entries WHERE ce_id = ? ORDER BY state_id",
                (rs, rowNum) -> new Counterexample.EntryCandidate(
                        rs.getString("state_id"),
                        rs.getInt("legal") == 1,
                        rs.getString("note")),
                id);
        return new Counterexample(id, modelId, name, propertyKind, description, trace, entries);
    }

    // ---------------------------------------------------------------- writes

    public void saveModel(Model model) {
        jdbc.update("INSERT OR REPLACE INTO models(id, name, description) VALUES (?,?,?)",
                model.id(), model.name(), model.description());
        jdbc.update("DELETE FROM states WHERE model_id = ?", model.id());
        int ord = 0;
        for (ModelState state : model.states()) {
            jdbc.update("INSERT INTO states(model_id, state_id, ord, variables, accept_sets, fair_sets) "
                            + "VALUES (?,?,?,?,?,?)",
                    model.id(), state.stateId(), ord++,
                    writeJson(state.variables()),
                    writeJson(state.acceptSets()),
                    writeJson(state.fairSets()));
        }
        jdbc.update("DELETE FROM transitions WHERE model_id = ?", model.id());
        int tord = 0;
        for (Transition t : model.transitions()) {
            jdbc.update("INSERT INTO transitions(model_id, ord, source, target, guard, description) "
                            + "VALUES (?,?,?,?,?,?)",
                    model.id(), tord++, t.source(), t.target(),
                    t.guard() == null ? "" : t.guard(), t.description());
        }
    }

    public void saveCounterexample(Counterexample ce) {
        jdbc.update("INSERT OR REPLACE INTO counterexamples(id, model_id, name, property_kind, description) "
                        + "VALUES (?,?,?,?,?)",
                ce.id(), ce.modelId(), ce.name(), ce.propertyKind(), ce.description());
        jdbc.update("DELETE FROM ce_traces WHERE ce_id = ?", ce.id());
        for (int i = 0; i < ce.trace().size(); i++) {
            jdbc.update("INSERT INTO ce_traces(ce_id, pos, state_id) VALUES (?,?,?)",
                    ce.id(), i, ce.trace().get(i));
        }
        jdbc.update("DELETE FROM ce_entries WHERE ce_id = ?", ce.id());
        for (Counterexample.EntryCandidate entry : ce.entries()) {
            jdbc.update("INSERT INTO ce_entries(ce_id, state_id, legal, note) VALUES (?,?,?,?)",
                    ce.id(), entry.stateId(), entry.legal() ? 1 : 0, entry.note());
        }
    }

    public int countRows(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    public void log(String action, String detail) {
        jdbc.update("INSERT INTO run_logs(created_at, action, detail) VALUES (datetime('now'),?,?)",
                action, detail);
    }

    public List<Map<String, Object>> recentLogs(int limit) {
        return jdbc.queryForList(
                "SELECT id, created_at, action, detail FROM run_logs ORDER BY id DESC LIMIT ?",
                limit);
    }

    public void wipeAllData() {
        jdbc.execute("DELETE FROM run_logs");
        jdbc.execute("DELETE FROM ce_entries");
        jdbc.execute("DELETE FROM ce_traces");
        jdbc.execute("DELETE FROM counterexamples");
        jdbc.execute("DELETE FROM transitions");
        jdbc.execute("DELETE FROM states");
        jdbc.execute("DELETE FROM models");
    }

    public void deleteCounterexampleTraces(String ceId) {
        jdbc.update("DELETE FROM ce_entries WHERE ce_id = ?", ceId);
        jdbc.update("DELETE FROM ce_traces WHERE ce_id = ?", ceId);
    }

    // ---------------------------------------------------------------- json

    private Map<String, Object> readMap(String json) {
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("变量 JSON 解析失败: " + json, e);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return mapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("标签 JSON 解析失败: " + json, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
