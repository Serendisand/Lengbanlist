package org.leng.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiSessionManager {
    private final ConcurrentHashMap<UUID, Map<String, Integer>> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> views = new ConcurrentHashMap<>();

    public int getPage(UUID player, String view) {
        Map<String, Integer> playerPages = pages.get(player);
        if (playerPages == null) {
            return 0;
        }
        Integer page = playerPages.get(view);
        return page == null ? 0 : page;
    }

    public void setPage(UUID player, String view, int page) {
        pages.computeIfAbsent(player, key -> new ConcurrentHashMap<>()).put(view, page);
    }

    public String getView(UUID player) {
        return views.get(player);
    }

    public void setView(UUID player, String view) {
        if (view == null || view.isEmpty()) {
            views.remove(player);
        } else {
            views.put(player, view);
        }
    }

    public void clear(UUID player) {
        pages.remove(player);
        views.remove(player);
    }
}
