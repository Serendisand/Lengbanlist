package org.leng.gui;

import org.junit.jupiter.api.Test;
import org.leng.object.ReportEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiKitTest {

    @Test
    void shortIdKeepsFirstEightCharacters() {
        assertEquals("12345678", GuiKit.shortId("12345678-aaaa-bbbb"));
        assertEquals("abc", GuiKit.shortId("abc"));
        assertEquals("?", GuiKit.shortId(null));
    }

    @Test
    void statusTextAddsColours() {
        assertTrue(GuiKit.statusText(ReportEntry.STATUS_PENDING).startsWith("§e"));
        assertTrue(GuiKit.statusText(ReportEntry.STATUS_ACCEPTED).startsWith("§b"));
        assertTrue(GuiKit.statusText(ReportEntry.STATUS_CLOSED).startsWith("§7"));
        assertTrue(GuiKit.statusText(null).contains(ReportEntry.STATUS_PENDING));
    }

    @Test
    void wrapsLongTextByWidth() {
        assertEquals(List.of("abcd", "efgh", "ij"), GuiKit.wrap("abcdefghij", 4));
    }

    @Test
    void wrapStripsColourCodesAndNewlines() {
        assertEquals(List.of("你好 世界"), GuiKit.wrap("§c你好\n世界", 10));
    }

    @Test
    void wrapEmptyTextReturnsOneLine() {
        assertEquals(List.of(""), GuiKit.wrap(null, 10));
    }
}
