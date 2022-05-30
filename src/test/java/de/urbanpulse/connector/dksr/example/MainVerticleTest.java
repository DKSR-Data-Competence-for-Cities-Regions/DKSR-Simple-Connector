package de.urbanpulse.connector.dksr.example;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.vertx.core.http.HttpMethod.PUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private JsonObject config;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().readFile("config.json", testContext.succeeding(buffer -> {
            config = buffer.toJsonObject();
            testContext.completeNow();
        }));
    }

    @Test
    void testConnectorSendingData(Vertx vertx, VertxTestContext testContext) throws Throwable {
        final Checkpoint checkpoint = testContext.checkpoint(3);
        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(request -> {
            testContext.verify(() -> {
                assertEquals(PUT, request.method());
                assertEquals("/", request.path());

                final MultiMap headers = request.headers();
                assertTrue(headers.contains("UrbanPulse-Timestamp"));
                assertTrue(headers.contains("Authorization"));
            });

            request.bodyHandler(buffer -> {
                final JsonObject body = buffer.toJsonObject();
                testContext.verify(() -> {
                    assertEquals(1, body.size());
                    assertTrue(body.containsKey("data"));
                    final JsonArray data = body.getJsonArray("data");
                    assertEquals(1, data.size());
                    final JsonObject event = data.getJsonObject(0);
                    assertEquals(3, event.size());
                    assertTrue(event.containsKey("SID"));
                    assertTrue(event.containsKey("timestamp"));
                    assertTrue(event.containsKey("value"));
                });
                request.response().setStatusCode(204).end();
                checkpoint.flag();
            });
        });
        httpServer.listen(0, testContext.succeeding(result -> {
            final JsonObject verticleCfg = getConfig(config, result.actualPort());
            vertx.deployVerticle(new MainVerticle(),
                    new DeploymentOptions().setConfig(verticleCfg).setInstances(1),
                    testContext.succeeding(id -> checkpoint.flag()));
        }));
    }

    private static JsonObject getConfig(JsonObject config, int actualPort) {
        final JsonObject receiverConfig = new JsonObject();
        receiverConfig.put("host", "0.0.0.0");
        receiverConfig.put("port", actualPort);
        receiverConfig.put("useSsl", false);
        receiverConfig.put("trustAll", false);

        final JsonObject verticleConfig = config.copy();
        verticleConfig.put("receiver", receiverConfig);
        verticleConfig.put("interval", 150);
        return verticleConfig;
    }
}
