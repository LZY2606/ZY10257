package dev.lasso.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BenchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void indexPageShowsTitle() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    if (!body.contains("反例套索查验台")) {
                        throw new AssertionError("页面缺少标题 反例套索查验台");
                    }
                });
    }

    @Test
    void checkFourScenarios() throws Exception {
        mvc.perform(get("/api/check").param("entry", "9").param("fairness", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict", is("VIOLATION_REPRODUCED")));
        mvc.perform(get("/api/check").param("entry", "9").param("fairness", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict", is("VIOLATION_REPRODUCED")));
        mvc.perform(get("/api/check").param("entry", "6").param("fairness", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict", is("VIOLATION_REPRODUCED")));
        mvc.perform(get("/api/check").param("entry", "6").param("fairness", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict", is("FAIRNESS_VIOLATED")))
                .andExpect(jsonPath("$.fairnessCoverage[0].status", is("PREFIX_ONLY")));
    }

    @Test
    void reductionsExposeTiedCandidates() throws Exception {
        mvc.perform(get("/api/reduce").param("kind", "STATE").param("entry", "9").param("fairness", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.candidates[0].score", is(14)));
        mvc.perform(get("/api/reduce").param("kind", "VARIABLE").param("entry", "9").param("fairness", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.candidates[0].score", is(3)));
        mvc.perform(get("/api/reduce").param("kind", "ITERATION").param("entry", "9").param("fairness", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.candidates[0].score", is(1)));
    }

    @Test
    void clearExportImportReverifies() throws Exception {
        mvc.perform(get("/api/check").param("entry", "6").param("fairness", "true")).andExpect(status().isOk());
        mvc.perform(get("/api/reduce").param("kind", "STATE").param("entry", "9").param("fairness", "false"))
                .andExpect(status().isOk());

        String exported = mvc.perform(get("/api/records/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records", hasSize(greaterThanOrEqualTo(2))))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(delete("/api/records")).andExpect(status().isOk());
        mvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/records/import").contentType("application/json").content(exported))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.mismatched", is(0)));

        mvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source", org.hamcrest.Matchers.startsWith("IMPORTED")));
    }
}
