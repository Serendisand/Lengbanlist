package org.leng.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.object.AuditEntry;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RollbackManagerTest {

    private final RollbackManager manager = new RollbackManager(null);

    private AuditEntry entryWithReason(String reason) {
        return new AuditEntry(0, System.currentTimeMillis(), "actor", "取消警告", "target", reason, true, "");
    }

    @Test
    void extractWarnIds_singleId_returnsSingleElementList() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 42"));
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertEquals("42", ids.get(0));
    }

    @Test
    void extractWarnIds_multipleIdsCommaDelimited_returnsAllInOrder() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: 2,警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_multipleIdsSpaceDelimited_returnsAll() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1 警告ID: 2 警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_mixedDelimiters_handlesBoth() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: 2 警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_emptyReason_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason("")));
    }

    @Test
    void extractWarnIds_nullReason_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason(null)));
    }

    @Test
    void extractWarnIds_reasonWithoutPrefix_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason("其他原因")));
    }

    @Test
    void extractWarnIds_trailingCommaWithEmptyId_filtersEmpty() {

        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: "));
        assertNotNull(ids);
        assertEquals(List.of("1"), ids);
    }

    @Test
    void extractWarnIds_nonNumericId_preservedAsString() {

        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: abc-123"));
        assertNotNull(ids);
        assertEquals(List.of("abc-123"), ids);
    }

    @Test
    void extractWarnIds_prefixOnly_returnsNull() {

        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: "));

        assertNull(ids);
    }
}
