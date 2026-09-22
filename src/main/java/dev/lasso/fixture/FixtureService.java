package dev.lasso.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lasso.model.Model;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class FixtureService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Model model;

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("fixture.json").getInputStream()) {
            this.model = objectMapper.readValue(in, Model.class);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载固定夹具 fixture.json", e);
        }
    }

    public Model getModel() {
        return model;
    }
}
