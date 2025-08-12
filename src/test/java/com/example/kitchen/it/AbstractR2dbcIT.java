package com.example.kitchen.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.codec.max-in-memory-size=10MB"
})
public abstract class AbstractR2dbcIT {

    @Container
    public static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("kitchen")
            .withUsername("postgres")
            .withPassword("postgres")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        PG.start();
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s", PG.getHost(), PG.getMappedPort(5432), PG.getDatabaseName()));
        registry.add("spring.r2dbc.username", PG::getUsername);
        registry.add("spring.r2dbc.password", PG::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    public static class ContainerInit implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            PG.start();

            String r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s",
                    PG.getHost(), PG.getFirstMappedPort(), PG.getDatabaseName());

            TestPropertyValues.of(
                    "spring.r2dbc.url=" + r2dbcUrl,
                    "spring.r2dbc.username=" + PG.getUsername(),
                    "spring.r2dbc.password=" + PG.getPassword(),
                    "spring.flyway.enabled=false"
            ).applyTo(ctx.getEnvironment());
        }

        @BeforeEach
        void initSchema(DatabaseClient client) throws Exception {
            // Execute test schema.sql (idempotent)
            String ddl = StreamUtils.copyToString(
                    getClass().getResourceAsStream("/schema.sql"), StandardCharsets.UTF_8);
            for (String stmt : ddl.split(";")) {
                String s = stmt.trim();
                if (!s.isEmpty()) {
                    client.sql(s).fetch().rowsUpdated().block();
                }
            }
            // Clean tables between tests
            client.sql("DELETE FROM actions").fetch().rowsUpdated().block();
            client.sql("DELETE FROM orders").fetch().rowsUpdated().block();
            // Quick sanity
            Integer cnt = client.sql("SELECT COUNT(*) FROM actions").map((row, md) -> row.get(0, Integer.class)).one().block();
            assertThat(cnt).isNotNull();
        }
    }
}
