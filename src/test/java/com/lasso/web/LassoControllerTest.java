package com.lasso.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LassoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageHasTitle() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("反例套索查验台")));
    }

    @Test
    void verifyEndpointSwitchesEntryAndFairness() throws Exception {
        mockMvc.perform(post("/api/verify")
                        .contentType("application/json")
                        .content("""
                                {"ceId":"CE1","entry":"s4","fairness":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violationReproduced").value(true))
                .andExpect(jsonPath("$.fairSatisfied[0]").value("FAIR"));

        mockMvc.perform(post("/api/verify")
                        .contentType("application/json")
                        .content("""
                                {"ceId":"CE1","entry":"s6","fairness":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violationReproduced").value(false))
                .andExpect(jsonPath("$.fairUnsatisfied[0]").value("FAIR"));
    }

    @Test
    void reductionsReturnCandidatesAndDiagramRendersSvg() throws Exception {
        mockMvc.perform(post("/api/reduce")
                        .contentType("application/json")
                        .content("""
                                {"ceId":"CE1","entry":"s4","fairness":false,"kind":"state"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.tie").value(true));

        mockMvc.perform(post("/api/reduce")
                        .contentType("application/json")
                        .content("""
                                {"ceId":"CE1","entry":"s4","fairness":false,"kind":"variables"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("variables"));

        mockMvc.perform(post("/api/diagram")
                        .contentType("application/json")
                        .content("""
                                {"ceId":"CE1","entry":"s4","fairness":true,"deletedStates":[],"keptVariables":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.svg").value(org.hamcrest.Matchers.containsString("<svg")));
    }

    @Test
    void runsExportedAndLogged() throws Exception {
        mockMvc.perform(post("/api/export")).andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("lasso-checker-export/v1"));
        mockMvc.perform(get("/api/runs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.logs.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
