package com.lasso.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasso.engine.LassoChecker;
import com.lasso.engine.ReductionEngine;
import com.lasso.model.CheckResult;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ReductionReport;
import com.lasso.persistence.Fixtures;
import com.lasso.persistence.LassoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LassoService {

    private final LassoRepository repository;
    private final ObjectMapper mapper;

    public LassoService(LassoRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("models", repository.findAllModels());
        data.put("counterexamples", repository.findAllCounterexamples());
        return data;
    }

    public CheckResult verify(String ceId, String entry, boolean fairness) {
        Counterexample ce = mustCe(ceId);
        Model model = mustModel(ce.modelId());
        CheckResult result = new LassoChecker(model).check(ce, entry, fairness);
        repository.log("VERIFY", ceId + " entry=" + entry + " fairness=" + fairness
                + " violation=" + result.violationReproduced());
        return result;
    }

    public ReductionReport reduce(String ceId, String entry, boolean fairness, String kind) {
        Counterexample ce = mustCe(ceId);
        Model model = mustModel(ce.modelId());
        ReductionEngine engine = new ReductionEngine(model);
        ReductionReport report = switch (kind) {
            case "state" -> engine.reduceStates(ce, entry, fairness);
            case "variables" -> engine.reduceVariables(ce, entry, fairness);
            case "iterations" -> engine.reduceIterations(ce, entry, fairness);
            default -> throw new IllegalArgumentException("未知缩减类型: " + kind);
        };
        repository.log("REDUCE", ceId + " entry=" + entry + " fairness=" + fairness
                + " kind=" + kind + " best=" + report.bestMetric()
                + " candidates=" + report.candidates().size());
        return report;
    }

    public Map<String, Object> runHistory(int limit) {
        return Map.of("logs", repository.recentLogs(limit));
    }

    public Map<String, Object> exportAll() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", "lasso-checker-export/v1");
        payload.put("exportedAt", java.time.Instant.now().toString());
        payload.put("data", overview());
        payload.put("runLogs", repository.recentLogs(500));
        repository.log("EXPORT", "导出全部模型/反例与运行记录");
        return payload;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importAll(String json, boolean reseedAfterWipe) {
        if (!reseedAfterWipe && (json == null || json.isBlank())) {
            throw new IllegalArgumentException("导入内容为空");
        }
        Map<String, Object> payload = Map.of();
        if (reseedAfterWipe == false && json != null && !json.isBlank()) {
            try {
                payload = mapper.readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("导入 JSON 无法解析: " + e.getMessage(), e);
            }
        }
        repository.wipeAllData();
        int models = 0;
        int ces = 0;
        if (!reseedAfterWipe && payload.get("data") instanceof Map<?, ?> data) {
            if (data.get("models") instanceof List<?> rawModels) {
                for (Object raw : rawModels) {
                    Model model = mapper.convertValue(raw, Model.class);
                    repository.saveModel(model);
                    models++;
                }
            }
            if (data.get("counterexamples") instanceof List<?> rawCes) {
                for (Object raw : rawCes) {
                    Counterexample ce = mapper.convertValue(raw, Counterexample.class);
                    repository.saveCounterexample(ce);
                    ces++;
                }
            }
        } else {
            repository.saveModel(Fixtures.modelOne());
            repository.saveCounterexample(Fixtures.ceOne());
            repository.saveModel(Fixtures.modelTwo());
            repository.saveCounterexample(Fixtures.ceTwo());
            models = 2;
            ces = 2;
        }
        repository.log("IMPORT", reseedAfterWipe
                ? "清空后重新导入固定夹具"
                : "清空后导入导出快照: models=" + models + " counterexamples=" + ces);
        return Map.of("imported", Map.of("models", models, "counterexamples", ces),
                "overview", overview());
    }

    public Map<String, Object> resetFixtures() {
        return importAll("", true);
    }

    private Counterexample mustCe(String ceId) {
        Counterexample ce = repository.findCounterexample(ceId);
        if (ce == null) {
            throw new IllegalArgumentException("反例不存在: " + ceId);
        }
        return ce;
    }

    private Model mustModel(String modelId) {
        Model model = repository.findModel(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        return model;
    }
}
