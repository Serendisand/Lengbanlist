package org.leng.object;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PlayerIdentity(
        String uuid,
        String name,
        List<String> knownNames
) {

    public static final PlayerIdentity EMPTY = new PlayerIdentity("", "", List.of());

    public PlayerIdentity {
        uuid = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        name = name == null ? "" : name;
        knownNames = knownNames == null ? List.of() : List.copyOf(knownNames);
    }

    public static PlayerIdentity ofName(String name) {
        return new PlayerIdentity("", name, name == null || name.isEmpty() ? List.of() : List.of(name));
    }

    public boolean hasUuid() {
        return !uuid.isEmpty();
    }

    public List<String> lowerNames() {
        Set<String> lower = new LinkedHashSet<>();
        if (!name.isEmpty()) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        for (String candidate : knownNames) {
            if (candidate != null && !candidate.isEmpty()) {
                lower.add(candidate.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(lower);
    }
}
