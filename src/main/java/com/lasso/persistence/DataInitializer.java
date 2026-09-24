package com.lasso.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final LassoRepository repository;

    public DataInitializer(LassoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.countRows("models") == 0) {
            repository.saveModel(Fixtures.modelOne());
            repository.saveCounterexample(Fixtures.ceOne());
            repository.saveModel(Fixtures.modelTwo());
            repository.saveCounterexample(Fixtures.ceTwo());
            repository.log("SEED", "首次启动，写入固定夹具 M1/CE1 与 M2/CE2");
            log.info("数据库为空，已写入内置固定夹具");
        }
    }
}
