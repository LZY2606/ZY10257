package dev.lasso.web;

import dev.lasso.engine.ReductionResult;
import dev.lasso.engine.ReplayResult;
import dev.lasso.repo.RunRecord;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BenchController {

    private final BenchService service;

    public BenchController(BenchService service) {
        this.service = service;
    }

    @GetMapping("/model")
    public Map<String, Object> model() {
        return service.model();
    }

    @GetMapping("/check")
    public ReplayResult check(@RequestParam int entry, @RequestParam(defaultValue = "false") boolean fairness) {
        return service.check(entry, fairness);
    }

    @GetMapping("/reduce")
    public ReductionResult reduce(@RequestParam String kind,
                                  @RequestParam int entry,
                                  @RequestParam(defaultValue = "false") boolean fairness) {
        return service.reduce(kind, entry, fairness);
    }

    @GetMapping("/records")
    public List<RunRecord> records() {
        return service.records();
    }

    @DeleteMapping("/records")
    public Map<String, Object> clear() {
        service.clear();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cleared", true);
        return out;
    }

    @GetMapping(value = "/records/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportRecords() {
        return service.exportRecords();
    }

    @PostMapping(value = "/records/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> importRecords(@RequestBody String body) {
        return service.importRecords(body);
    }
}
