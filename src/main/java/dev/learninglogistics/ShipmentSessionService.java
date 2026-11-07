package dev.learninglogistics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

public final class ShipmentSessionService {
    public enum Outcome { DELIVERED, EXCEPTION }

    public record StartSession(String shipmentId, String courierId, String courierName) {}
    public record SessionAccess(String shipmentId, String room, String participantToken) {}
    public record ArchiveRequest(String shipmentId, Outcome outcome, String mediaType, long bytes) {}
    public record ArchiveTarget(String bucket, String objectKey, String uploadUrl) {}

    private final InfraiGateway infrai;
    private final ServiceConfiguration configuration;
    private final Clock clock;

    public ShipmentSessionService(InfraiGateway infrai, ServiceConfiguration configuration) {
        this(infrai, configuration, Clock.systemUTC());
    }

    ShipmentSessionService(InfraiGateway infrai, ServiceConfiguration configuration, Clock clock) {
        this.infrai = infrai;
        this.configuration = configuration;
        this.clock = clock;
    }

    public void preparePrivateArchive() {
        infrai.createBucket(configuration.bucket());
    }

    public SessionAccess start(StartSession command) {
        String room = "shipment-" + safe(command.shipmentId());
        infrai.createRoom(room);
        Map<String, Object> token = infrai.issueRoomToken(room, command.courierId(), command.courierName());
        return new SessionAccess(command.shipmentId(), room, requiredText(token, "token"));
    }

    public ArchiveTarget finish(ArchiveRequest command) {
        String key = objectKey(command.shipmentId(), command.outcome(), LocalDate.now(clock));
        String requestId = "archive-" + safe(command.shipmentId()) + "-" + command.outcome().name().toLowerCase();
        Map<String, Object> presign = infrai.presignArtefactUpload(
                configuration.bucket(), key, command.mediaType(), command.bytes(), requestId);
        return new ArchiveTarget(configuration.bucket(), key, requiredText(presign, "url"));
    }

    public static String objectKey(String shipmentId, Outcome outcome, LocalDate date) {
        String lane = outcome == Outcome.DELIVERED ? "proof-of-delivery" : "exceptions";
        return date + "/" + lane + "/" + safe(shipmentId) + "/session.webm";
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Identifiers may contain letters, digits, underscore, and hyphen");
        }
        return value;
    }

    private static String requiredText(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Infrai response is missing " + name);
        }
        return text;
    }
}
