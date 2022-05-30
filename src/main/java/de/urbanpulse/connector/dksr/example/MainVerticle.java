package de.urbanpulse.connector.dksr.example;

import de.urbanpulse.connector.dksr.example.auth.HMACAuthenticationVerticle;
import de.urbanpulse.connector.dksr.example.util.UPDateTimeUtils;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * This is the main entry point of the DKSR example connector. It sends random sensor events in a
 * preconfigured interval.
 *
 * @author <a href="mailto:david.krueger@the-urban-institute.de">David Krüger</a>
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
    private HMACAuthenticationVerticle authenticationVerticle;
    private String sensorId;
    private WebClient webClient;
    private long timerId;

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("Starting DKSR example connector...");
        final JsonObject config = config();
        sensorId = config.getString("sensorId");

        LOGGER.info("Deploying HMACAuthenticationVerticle...");
        final JsonObject credentials = config.getJsonObject("credentials");
        authenticationVerticle = new HMACAuthenticationVerticle();
        vertx.deployVerticle(authenticationVerticle,
                new DeploymentOptions().setInstances(1).setConfig(credentials), deployment -> {
                    if (deployment.failed()) {
                        startPromise.fail(deployment.cause());
                    }
                });

        final JsonObject receiverConfig = config.getJsonObject("receiver");
        final WebClientOptions clientOptions = new WebClientOptions();
        clientOptions.setDefaultHost(receiverConfig.getString("host"))
                .setDefaultPort(receiverConfig.getInteger("port"))
                .setSsl(receiverConfig.getBoolean("useSsl"))
                .setTrustAll(receiverConfig.getBoolean("trustAll"));
        webClient = WebClient.create(vertx, clientOptions);

        final Integer sendInterval = config.getInteger("interval", 15000);
        timerId = vertx.setPeriodic(sendInterval, this::sendRandomSensorEventData);
        LOGGER.info("Successfully started connector, sending events every {} sec!", sendInterval / 1000.0);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        vertx.cancelTimer(timerId);
        webClient.close();
        vertx.undeploy(authenticationVerticle.deploymentID(), stopPromise);
    }

    private void sendRandomSensorEventData(long unused) {
        final JsonObject sensorEventData = generateSensorEventData();
        LOGGER.info("Sending sensor event data");
        final Future<Message<JsonObject>> headersFuture = vertx.eventBus()
                .request(HMACAuthenticationVerticle.ADDRESS, sensorEventData.encode());
        LOGGER.info("Acquiring authentication info...");
        headersFuture.onComplete(
                headersAsync -> sendRequest(headersAsync, sensorEventData));
    }

    private void sendRequest(AsyncResult<Message<JsonObject>> headersAsync,
            JsonObject sensorEventData) {
        if (headersAsync.succeeded()) {
            final Message<JsonObject> headersMsg = headersAsync.result();
            final JsonObject headers = headersMsg.body();
            final HttpRequest<Buffer> httpRequest = webClient.put("/");
            headers.forEach(
                    entry -> httpRequest.putHeader(entry.getKey(), (String) entry.getValue()));
            LOGGER.info("Set request authentication headers, now sending...");
            httpRequest.sendJsonObject(sensorEventData, this::handleResponse);
        } else {
            LOGGER.error("Failed to receive authentication headers", headersAsync.cause());
        }
    }

    private void handleResponse(AsyncResult<HttpResponse<Buffer>> requestAsync) {
        if (requestAsync.succeeded()) {
            final HttpResponse<Buffer> response = requestAsync.result();
            final int statusCode = response.statusCode();
            if (statusCode == 204) {
                LOGGER.info("Sensor event data was sent successfully : {}", statusCode);
            } else {
                LOGGER.warn("Sensor event data was sent, however received HTTP {} : {}", statusCode,
                        response.bodyAsString());
            }
        } else {
            LOGGER.error("Failed to send sensor event", requestAsync.cause());
        }
    }

    private JsonObject generateSensorEventData() {
        final JsonObject requestBody = new JsonObject();
        final JsonObject sensorEvent = new JsonObject();
        sensorEvent.put("SID", sensorId);
        sensorEvent.put("timestamp", UPDateTimeUtils.getUpTimestampNow());
        sensorEvent.put("value", Math.random());
        requestBody.put("data", Collections.singletonList(sensorEvent));
        return requestBody;
    }
}
