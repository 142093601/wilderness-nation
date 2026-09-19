package com.wildernessnation.statecraft.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CultureTest {

    @Test
    void acceptsAValidCulture() {
        Culture c = new Culture("bandit", "匪帮", List.of("赤沙", "黑岩"));
        assertEquals("bandit", c.id());
        assertEquals("匪帮", c.displayName());
        assertEquals(List.of("赤沙", "黑岩"), c.namePrefixes());
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new Culture(null, "匪帮", List.of("赤沙")));
        assertThrows(IllegalArgumentException.class, () -> new Culture("  ", "匪帮", List.of("赤沙")));
    }

    @Test
    void rejectsMissingNamePool() {
        assertThrows(IllegalArgumentException.class, () -> new Culture("bandit", "匪帮", null));
        assertThrows(IllegalArgumentException.class, () -> new Culture("bandit", "匪帮", List.of()));
    }

    @Test
    void namePoolIsADefensiveCopy() {
        List<String> mutable = new ArrayList<>();
        mutable.add("赤沙");
        Culture c = new Culture("bandit", "匪帮", mutable);
        mutable.add("黑岩");
        assertEquals(1, c.namePrefixes().size());
        assertThrows(UnsupportedOperationException.class, () -> c.namePrefixes().add("x"));
    }
}
