package com.upc.wms.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanNoFormatterTest {

    @Test
    void display_normalizesShortPlanNoToFiveDigits() {
        assertEquals("PP2026070800003", PlanNoFormatter.display("PP202607080003", 3L));
        assertEquals("PP2026070800010", PlanNoFormatter.display("PP2026070800010", 10L));
    }

    @Test
    void display_convertsLegacyLongPlanNo() {
        assertEquals("PP2026070800009", PlanNoFormatter.display("PP20260708192310250176", 9L));
    }

    @Test
    void next_incrementsFromExistingShortNumbers() {
        String next = PlanNoFormatter.next(List.of(
                "PP202607080003",
                "PP2026070800010",
                "PP20260708192310250176"
        ));
        assertTrue(next.matches("^PP\\d{8}\\d{5}$"));
        assertEquals("PP2026070800011", next);
    }
}
