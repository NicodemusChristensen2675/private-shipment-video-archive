package dev.learninglogistics;

import java.time.LocalDate;

public final class ShipmentSessionServiceTest {
    public static void main(String[] args) {
        LocalDate lessonDay = LocalDate.of(2026, 9, 25);
        assertEquals(
                "2026-09-25/proof-of-delivery/SHIP-2048/session.webm",
                ShipmentSessionService.objectKey("SHIP-2048", ShipmentSessionService.Outcome.DELIVERED, lessonDay));
        assertEquals(
                "2026-09-25/exceptions/SHIP-2048/session.webm",
                ShipmentSessionService.objectKey("SHIP-2048", ShipmentSessionService.Outcome.EXCEPTION, lessonDay));
        System.out.println("PASS: delivery and exception artefacts use separate private archive lanes");
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
