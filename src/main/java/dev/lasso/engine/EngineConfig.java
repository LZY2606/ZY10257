package dev.lasso.engine;

import dev.lasso.fixture.FixtureService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    ReplayEngine replayEngine(FixtureService fixtureService) {
        return new ReplayEngine(fixtureService.getModel());
    }

    @Bean
    ReductionEngine reductionEngine(ReplayEngine replayEngine) {
        return new ReductionEngine(replayEngine);
    }
}
