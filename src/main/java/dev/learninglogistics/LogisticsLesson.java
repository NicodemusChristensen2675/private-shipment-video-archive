package dev.learninglogistics;

import java.util.Map;

public final class LogisticsLesson {
    private LogisticsLesson() {}

    public static void main(String[] args) {
        ServiceConfiguration configuration = ServiceConfiguration.fromEnvironment();
        InfraiGateway gateway = new InfraiGateway(configuration);
        ShipmentSessionService service = new ShipmentSessionService(gateway, configuration);

        String shipmentId = args.length > 0 ? args[0] : "SHIP-2048";
        ShipmentSessionService.Outcome outcome = args.length > 1
                ? ShipmentSessionService.Outcome.valueOf(args[1].toUpperCase())
                : ShipmentSessionService.Outcome.DELIVERED;

        service.preparePrivateArchive();
        ShipmentSessionService.SessionAccess session = service.start(
                new ShipmentSessionService.StartSession(shipmentId, "courier-17", "Morgan Lee"));
        ShipmentSessionService.ArchiveTarget archive = service.finish(
                new ShipmentSessionService.ArchiveRequest(shipmentId, outcome, "video/webm", 200_000_000));

        System.out.println(Json.stringify(Map.of(
                "shipment_id", session.shipmentId(),
                "room", session.room(),
                "participant_token", session.participantToken(),
                "archive_bucket", archive.bucket(),
                "archive_key", archive.objectKey(),
                "upload_url", archive.uploadUrl())));
    }
}
