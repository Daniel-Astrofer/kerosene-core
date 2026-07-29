package com.kerosene.content.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kerosene.common.service.TickerService;
import com.kerosene.content.dto.HomeStageDTO;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeMarketInsightServiceTest {

    @Mock
    private TickerService tickerService;

    @Test
    void stageUsesBelowStageAndOncePlayPolicy() {
        when(tickerService.getChange24hPercent("usd")).thenReturn(new BigDecimal("12.34"));
        when(tickerService.getPrice("usd")).thenReturn(new BigDecimal("67000"));

        HomeStageComposer composer = new HomeStageComposer(tickerService);
        HomeStageDTO stage = composer.compose("pt", "TOTAL");

        assertEquals("MARKET", stage.kind());
        assertEquals("ONCE", stage.playPolicy());
        assertEquals("BELOW_STAGE", stage.layout().actions().placement());
        assertEquals("ALWAYS_VISIBLE", stage.layout().actions().policy());
        assertTrue(Boolean.TRUE.equals(stage.motion().bodyShift().enabled()));
        assertEquals("EASE_OUT_CUBIC", stage.motion().bodyShift().curve());
        assertNotEquals("IDLE", stage.kind());
        assertTrue(stage.content().title().contains("subiu"));
        assertTrue(stage.atmosphere().glows().size() >= 2);
        assertEquals("positive", stage.atmosphere().glows().get(0).colorToken());
    }
}
