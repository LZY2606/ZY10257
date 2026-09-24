package com.lasso.web;

import com.lasso.model.CheckResult;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ReductionReport;
import com.lasso.persistence.LassoRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LassoController {

    private final LassoService service;
    private final LassoRepository repository;
    private final SvgService svgService;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public LassoController(LassoService service, LassoRepository repository,
                           SvgService svgService,
                           com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.service = service;
        this.repository = repository;
        this.svgService = svgService;
        this.mapper = mapper;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @PostMapping("/verify")
    public CheckResult verify(@RequestBody VerifyRequest request) {
        return service.verify(request.ceId(), request.entry(), request.fairness());
    }

    @PostMapping("/reduce")
    public ReductionReport reduce(@RequestBody ReduceRequest request) {
        return service.reduce(request.ceId(), request.entry(), request.fairness(), request.kind());
    }

    @PostMapping("/diagram")
    public Map<String, String> diagram(@RequestBody DiagramRequest request) {
        Counterexample ce = repository.findCounterexample(request.ceId());
        if (ce == null) {
            throw new IllegalArgumentException("反例不存在: " + request.ceId());
        }
        Model model = repository.findModel(ce.modelId());
        CheckResult result = new com.lasso.engine.LassoChecker(model)
                .check(ce, request.entry(), request.fairness());
        List<String> deleted = request.deletedStates() == null ? List.of() : request.deletedStates();
        List<String> keptVars = request.keptVariables() == null ? List.of() : request.keptVariables();
        String svg = svgService.render(model, ce, request.entry(), result.replay(),
                deleted, keptVars);
        return Map.of("svg", svg, "verdict", result.verdict());
    }

    @GetMapping("/runs")
    public Map<String, Object> runs(@RequestParam(defaultValue = "100") int limit) {
        return service.runHistory(limit);
    }

    @GetMapping(value = "/export", produces = "application/json;charset=UTF-8")
    public ResponseEntity<byte[]> exportData() {
        try {
            byte[] body = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(service.exportAll());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"lasso-export.json\"")
                    .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                    .body(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("导出序列化失败", e);
        }
    }

    @PostMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportJson() {
        return service.exportAll();
    }

    @PostMapping("/import")
    public Map<String, Object> importData(@RequestBody(required = false) String body) {
        return service.importAll(body, false);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return service.resetFixtures();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    public record VerifyRequest(String ceId, String entry, boolean fairness) {
        public VerifyRequest() {
            this(null, null, false);
        }
    }

    public record ReduceRequest(String ceId, String entry, boolean fairness, String kind) {
        public ReduceRequest() {
            this(null, null, false, null);
        }
    }

    public record DiagramRequest(String ceId, String entry, boolean fairness,
                                 List<String> deletedStates, List<String> keptVariables) {
        public DiagramRequest() {
            this(null, null, false, List.of(), List.of());
        }
    }
}
