package de.urbanpulse.connector.dksr.example.auth;

import de.urbanpulse.connector.dksr.example.util.UPDateTimeUtils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * This verticle registers an event bus consumer that generates headers for hmac based (UPConnector)
 * authentication, to send PUT requests to the inbound module of UrbanPulse.
 *
 * @author <a href="mailto:david.krueger@the-urban-institute.de">David Krüger</a>
 */
public class HMACAuthenticationVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HMACAuthenticationVerticle.class);
    public static final String ADDRESS = HMACAuthenticationVerticle.class.getName();
    private static final String ALGORITHM = "HmacSHA256";
    private static final String AUTH_TYPE = "UPConnector";
    private static final String HEADERS_AUTHORIZATION = "Authorization";
    private static final String HEADERS_URBAN_PULSE_TIMESTAMP = "UrbanPulse-Timestamp";

    private String connectorId;
    private String connectorKey;
    private MessageConsumer<String> consumer;

    @Override
    public void start() {
        final JsonObject config = config();
        connectorId = config.getString("connectorId");
        connectorKey = config.getString("connectorKey");
        consumer = vertx.eventBus().localConsumer(ADDRESS, this::generateAuthenticationHeaders);
        LOGGER.info("Successfully deployed HMACAuthenticationVerticle");
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        consumer.unregister(stopPromise);
    }

    private void generateAuthenticationHeaders(Message<String> incoming) {
        final String bodyToSend = incoming.body();
        LOGGER.info("Generating hmac authentication headers for body {}", bodyToSend);
        final String upTimestamp = UPDateTimeUtils.getUpTimestampNow();

        try {
            final JsonObject headers = new JsonObject();
            headers.put(HEADERS_AUTHORIZATION, generateAuthorizationHeader(upTimestamp, bodyToSend));
            headers.put(HEADERS_URBAN_PULSE_TIMESTAMP, upTimestamp);
            incoming.reply(headers);
            LOGGER.info("Successfully generated authentication headers");
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            incoming.fail(0, "Failed to generate authentication headers");
        }
    }

    private String generateAuthorizationHeader(String timestamp, String body)
            throws NoSuchAlgorithmException, InvalidKeyException {
        final String authorizationHash = generateAuthorizationHash(timestamp, body);
        return String.format("%s %s:%s", AUTH_TYPE, encodeBase64(connectorId), authorizationHash);
    }

    private String generateAuthorizationHash(String timestamp, String body)
            throws InvalidKeyException, NoSuchAlgorithmException {
        final SecretKeySpec keySpec =
                new SecretKeySpec(connectorKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        final Mac sha256 = Mac.getInstance(ALGORITHM);
        sha256.init(keySpec);
        final String input = timestamp + body;
        final byte[] rawBytes = input.getBytes(StandardCharsets.UTF_8);
        final byte[] hashedBytes = sha256.doFinal(rawBytes);
        return encodeBase64(hashedBytes);
    }

    private static String encodeBase64(String str) {
        final byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        return encodeBase64(strBytes);
    }

    private static String encodeBase64(byte[] rawBytes) {
        final Base64.Encoder encoder = Base64.getEncoder();
        final byte[] encodedBytes = encoder.encode(rawBytes);
        return new String(encodedBytes, StandardCharsets.UTF_8);
    }
}
