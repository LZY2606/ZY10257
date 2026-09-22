package dev.lasso.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lasso.engine.ReductionEngine;
import dev.lasso.engine.ReductionKind;
import dev.lasso.engine.ReductionResult;
import dev.lasso.engine.ReplayResult;
import dev.lasso.fixture.FixtureService;
import dev.lasso.repo.RunRecord;
import dev.lasso.repo.RunRecordRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BenchService {

    private final FixtureService fixtureService;
    private final ReductionEngine reductionEngine;
    private final RunRecordRepository repository;
    private final ObjectMapper objectMapper;

    public BenchService(FixtureService fixtureService, ReductionEngine reductionEngine,
                        RunRecordRepository repository, ObjectMapper objectMapper) {
        this.fixtureService = fixtureService;
        this.reductionEngine = reductionEngine;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> model() {
        return objectMapper.convertValue(fixtureService.getModel(), LinkedHashMap.class);
    }

    public ReplayResult check(int entryIndex, boolean fairnessEnabled) {
        ReplayResult result = reductionEngine.baseline(entryIndex, fairnessEnabled);
        persist("CHECK", entryIndex, fairnessEnabled, null, result, result, "LOCAL");
        return result;
    }

    public ReductionResult reduce(String kind, int entryIndex, boolean fairnessEnabled) {
        ReductionResult result = reductionEngine.reduce(ReductionKind.valueOf(kind), entryIndex, fairnessEnabled);
        persist("REDUCE_" + kind, entryIndex, fairnessEnabled, kind, result.baseline(), result, "LOCAL");
        return result;
    }

    public List<RunRecord> records() {
        return repository.findAll();
    }

    public void clear() {
        repository.clear();
    }

    public Map<String, Object> exportRecords() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RunRecord r : repository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("createdAt", r.createdAt());
            row.put("action", r.action());
            row.put("entryIndex", r.entryIndex());
            row.put("fairnessEnabled", r.fairnessEnabled());
            row.put("reductionKind", r.reductionKind());
            row.put("verdict", r.verdict());
            row.put("violationReproduced", r.violationReproduced());
            row.put("payload", parse(r.payloadJson()));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "lasso-bench-run-records-v1");
        out.put("fixture", fixtureService.getModel().name());
        out.put("exportedAt", now());
        out.put("records", rows);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> importRecords(String json) {
        Map<String, Object> doc = parse(json);
        Object rowsObj = doc.get("records");
        List<Map<String, Object>> rows = rowsObj instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        int imported = 0;
        int verified = 0;
        int mismatched = 0;
        List<Map<String, Object>> mismatchDetails = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int entryIndex = ((Number) row.getOrDefault("entryIndex", 0)).intValue();
            boolean fairnessEnabled = Boolean.TRUE.equals(row.get("fairnessEnabled"));
            String action = String.valueOf(row.getOrDefault("action", "IMPORT"));
            String kind = row.get("reductionKind") == null ? null : String.valueOf(row.get("reductionKind"));
            String storedVerdict = String.valueOf(row.getOrDefault("verdict", ""));
            boolean storedViolation = Boolean.TRUE.equals(row.get("violationReproduced"));
            String payload = write(row.get("payload"));

            ReplayResult current = reductionEngine.baseline(entryIndex, fairnessEnabled);
            boolean match = current.verdict().equals(storedVerdict) && current.violationReproduced() == storedViolation;
            if (match) {
                verified++;
            } else {
                mismatched++;
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("action", action);
                detail.put("entryIndex", entryIndex);
                detail.put("fairnessEnabled", fairnessEnabled);
                detail.put("storedVerdict", storedVerdict);
                detail.put("recomputedVerdict", current.verdict());
                mismatchDetails.add(detail);
            }
            repository.insert(now(), action, entryIndex, fairnessEnabled, kind, current.verdict(),
                    current.violationReproduced(), payload, "IMPORTED_" + (match ? "VERIFIED" : "MISMATCH"));
            imported++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("imported", imported);
        summary.put("verified", verified);
        summary.put("mismatched", mismatched);
        summary.put("mismatchDetails", mismatchDetails);
        return summary;
    }

    private void persist(String action, int entryIndex, boolean fairnessEnabled, String kind,
                         ReplayResult verdictSource, Object payload, String source) {
        repository.insert(now(), action, entryIndex, fairnessEnabled, kind,
                verdictSource.verdict(), verdictSource.violationReproduced(), write(payload), source);
    }

    private String now() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }
}
