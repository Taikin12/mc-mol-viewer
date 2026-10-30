package dev.taikin.mcplugins.mcmolview.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModelRegistry {

    private final Map<Integer, ViewSession> models = new LinkedHashMap<>();
    private final Map<UUID, Integer> active = new LinkedHashMap<>();
    private int nextId = 1;

    public List<ViewSession> models() {
        return List.copyOf(models.values());
    }

    public int size() {
        return models.size();
    }

    public ViewSession get(int id) {
        return models.get(id);
    }

    public ViewSession active(UUID playerId) {
        Integer id = active.get(playerId);
        return id == null ? null : models.get(id);
    }

    public int activeId(UUID playerId) {
        return active.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> activeMap() {
        return Map.copyOf(active);
    }

    public int add(UUID owner, ViewSession session) {
        int id = nextId++;
        session.id(id);
        session.owner(owner);
        models.put(id, session);
        active.put(owner, id);
        return id;
    }

    public void restore(int id, ViewSession session) {
        session.id(id);
        models.put(id, session);
        nextId = Math.max(nextId, id + 1);
    }

    public boolean select(UUID playerId, int id) {
        if (!models.containsKey(id)) {
            return false;
        }
        active.put(playerId, id);
        return true;
    }

    public void setActive(UUID playerId, int id) {
        if (models.containsKey(id)) {
            active.put(playerId, id);
        }
    }

    public ViewSession remove(int id) {
        ViewSession removed = models.remove(id);
        if (removed != null) {
            active.entrySet().removeIf(entry -> entry.getValue() == id);
        }
        return removed;
    }

    public int nextId() {
        return nextId;
    }

    public void nextId(int nextId) {
        this.nextId = Math.max(this.nextId, Math.max(1, nextId));
    }

    public void clear() {
        models.clear();
        active.clear();
        nextId = 1;
    }
}
