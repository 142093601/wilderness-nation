package com.wildernessnation.statecraft.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StatecraftConfigTest {

    @Test
    void defaultsMatchSpec() {
        StatecraftConfig c = StatecraftConfig.defaults();
        assertEquals(12, c.nationCount());
        assertEquals(1500, c.minNationSpacing());
        assertEquals(1, c.sizeMin());
        assertEquals(10, c.sizeMax());
        assertEquals(95.0, c.devBase(), 0.0);
        assertEquals(6.0, c.devPerSize(), 0.0);
        assertEquals(8.0, c.devJitter(), 0.0);
        assertEquals(20.0, c.devMin(), 0.0);
        assertEquals(60.0, c.stanceBase(), 0.0);
        assertEquals(30.0, c.stanceJitter(), 0.0);
    }

    @Test
    void validatedClampsOutOfRangeValues() {
        // 国家数 -5 → 钳到下限 8；内半径 100 < 国家间距 1500 → 抬到 1500
        StatecraftConfig wild = new StatecraftConfig(
                -5, 1500, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                100.0, 12000.0,
                8.0);
        StatecraftConfig c = wild.validated();
        assertEquals(8, c.nationCount(), "国家数下限 8");
        assertEquals(1500, c.minPlacementRadiusAsInt(), "内半径不能小于国家间距");
    }

    @Test
    void validatedKeepsGoodValues() {
        assertEquals(StatecraftConfig.defaults(), StatecraftConfig.defaults().validated());
    }

    @Test
    void rejectsNonSenseSpacing() {
        StatecraftConfig bad = new StatecraftConfig(
                12, 0, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                0.0, 0.0,
                8.0);
        assertThrows(IllegalStateException.class, bad::validated);
    }

    @Test
    void canRestrictSizeRange() {
        StatecraftConfig c = new StatecraftConfig(
                8, 1500, 3, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                2000.0, 12000.0,
                8.0).validated();
        assertEquals(3, c.sizeMin());
        assertEquals(10, c.sizeMax());
    }
}
