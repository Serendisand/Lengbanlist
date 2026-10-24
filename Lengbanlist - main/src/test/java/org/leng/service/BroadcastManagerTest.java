package org.leng.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BroadcastManagerTest {

    @Test
    void orderlyModeFollowsListOrderAndLoops() {
        List<String> messages = List.of("1", "2", "3");
        AtomicInteger cursor = new AtomicInteger();

        assertEquals("1", pick(messages, BroadcastManager.BroadcastMode.ORDERLY, cursor));
        assertEquals("2", pick(messages, BroadcastManager.BroadcastMode.ORDERLY, cursor));
        assertEquals("3", pick(messages, BroadcastManager.BroadcastMode.ORDERLY, cursor));
        assertEquals("1", pick(messages, BroadcastManager.BroadcastMode.ORDERLY, cursor));
    }

    @Test
    void orderlyModeHandlesMessageListShrink() {
        AtomicInteger cursor = new AtomicInteger();
        List<String> three = List.of("1", "2", "3");
        List<String> two = List.of("1", "2");

        pick(three, BroadcastManager.BroadcastMode.ORDERLY, cursor);
        pick(three, BroadcastManager.BroadcastMode.ORDERLY, cursor);

        assertEquals("1", pick(two, BroadcastManager.BroadcastMode.ORDERLY, cursor));
    }

    @Test
    void randomModeUsesSupplier() {
        List<String> messages = List.of("1", "2", "3");

        assertEquals("2", BroadcastManager.pickTemplate(messages, "default",
                BroadcastManager.BroadcastMode.RANDOM, new AtomicInteger(), () -> 1));
    }

    @Test
    void randomModeHandlesNegativeSupplierIndex() {
        List<String> messages = List.of("1", "2", "3");

        assertEquals("3", BroadcastManager.pickTemplate(messages, "default",
                BroadcastManager.BroadcastMode.RANDOM, new AtomicInteger(), () -> -1));
    }

    @Test
    void emptyMessagesFallBackToDefault() {
        assertEquals("default", BroadcastManager.pickTemplate(List.of(), "default",
                BroadcastManager.BroadcastMode.ORDERLY, new AtomicInteger(), () -> 0));
        assertEquals("default", BroadcastManager.pickTemplate(null, "default",
                BroadcastManager.BroadcastMode.RANDOM, new AtomicInteger(), () -> 0));
    }

    @Test
    void modeParserIsCaseInsensitiveAndFallsBackToRandom() {
        assertEquals(BroadcastManager.BroadcastMode.ORDERLY,
                BroadcastManager.BroadcastMode.from("orderly"));
        assertEquals(BroadcastManager.BroadcastMode.ORDERLY,
                BroadcastManager.BroadcastMode.from("ORDERLY"));
        assertEquals(BroadcastManager.BroadcastMode.RANDOM,
                BroadcastManager.BroadcastMode.from("random"));
        assertEquals(BroadcastManager.BroadcastMode.RANDOM,
                BroadcastManager.BroadcastMode.from(null));
        assertEquals(BroadcastManager.BroadcastMode.RANDOM,
                BroadcastManager.BroadcastMode.from("unknown"));
    }

    private String pick(List<String> messages, BroadcastManager.BroadcastMode mode, AtomicInteger cursor) {
        return BroadcastManager.pickTemplate(messages, "default", mode, cursor, () -> 0);
    }
}
