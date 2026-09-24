package com.lasso.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.web.LassoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ExportImportRoundTripTest {

    @Autowired
    private LassoService service;
    @Autowired
    private LassoRepository repository;
    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("导出 -> 清空 -> 重新导入 -> 复核判定与缩减结论一致")
    @SuppressWarnings("unchecked")
    void roundTripPreservesSemantics() throws Exception {
        var beforeFair = service.verify("CE1", "s4", true);
        var beforeUnfair = service.verify("CE1", "s6", true);
        assertTrue(beforeFair.violationReproduced());
        assertFalse(beforeUnfair.violationReproduced());
        var beforeReduction = service.reduce("CE1", "s4", false, "state");
        int beforeModels = repository.countRows("models");
        int beforeCes = repository.countRows("counterexamples");

        Map<String, Object> exported = service.exportAll();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(exported);

        var imported = service.importAll(json, false);
        assertEquals(beforeModels, repository.countRows("models"));
        assertEquals(beforeCes, repository.countRows("counterexamples"));
        assertEquals(beforeModels, ((Map<String, Integer>) imported.get("imported")).get("models"));

        Model m1 = repository.findModel("M1");
        Counterexample ce1 = repository.findCounterexample("CE1");
        assertNotNull(m1);
        assertNotNull(ce1);
        assertEquals(10, m1.states().size());
        assertEquals(List.of("s0", "s1", "s3", "s4", "s5", "s6", "s7", "s8"), ce1.trace());

        var afterFair = service.verify("CE1", "s4", true);
        var afterUnfair = service.verify("CE1", "s6", true);
        assertEquals(beforeFair.violationReproduced(), afterFair.violationReproduced());
        assertEquals(beforeUnfair.fairUnsatisfied(), afterUnfair.fairUnsatisfied());

        var afterReduction = service.reduce("CE1", "s4", false, "state");
        assertEquals(beforeReduction.bestMetric(), afterReduction.bestMetric());
        assertEquals(beforeReduction.candidates().size(), afterReduction.candidates().size());
    }

    @Test
    @DisplayName("恢复固定 fixture 后数据重新可用")
    void resetRestoresFixtures() {
        service.resetFixtures();
        assertNotNull(repository.findCounterexample("CE2"));
        assertEquals(3, repository.findModel("M2").states().size());
    }
}
