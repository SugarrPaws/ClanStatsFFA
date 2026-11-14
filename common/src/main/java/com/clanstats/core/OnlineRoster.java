package com.clanstats.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Lista de jugadores conectados (tomada de la lista de Tab) con un margen de gracia:
 * si alguien se sale justo después de morir, su nombre (y su UUID) siguen contando un rato.
 */
public final class OnlineRoster implements NameOracle {
    private static final long GRACE_MS = 120_000L;

    private final Map<String, String> display = new HashMap<>();
    private final Map<String, String> uuids = new HashMap<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final LongSupplier clock;
    private List<PlayerRef> current = List.of();

    public OnlineRoster() {
        this(System::currentTimeMillis);
    }

    public OnlineRoster(LongSupplier clock) {
        this.clock = clock;
    }

    public void update(Collection<PlayerRef> players) {
        long now = clock.getAsLong();
        List<PlayerRef> list = new ArrayList<>();
        for (PlayerRef p : players) {
            if (p == null || !Names.isValid(p.name())) continue;
            String k = Names.key(p.name());
            display.put(k, p.name());
            lastSeen.put(k, now);
            if (p.uuid() != null) uuids.put(k, p.uuid());
            list.add(p);
        }
        current = List.copyOf(list);
        if (lastSeen.size() > 3000) {
            lastSeen.entrySet().removeIf(e -> now - e.getValue() > GRACE_MS);
            display.keySet().retainAll(lastSeen.keySet());
            uuids.keySet().retainAll(lastSeen.keySet());
        }
    }

    /** Igual que {@link #update(Collection)} pero solo con nombres (sin UUID). */
    public void updateNames(Collection<String> names) {
        List<PlayerRef> list = new ArrayList<>();
        for (String n : names) list.add(new PlayerRef(n, null));
        update(list);
    }

    @Override
    public boolean available() {
        return current.size() >= 2;
    }

    @Override
    public boolean isKnown(String name) {
        if (name == null) return false;
        Long t = lastSeen.get(Names.key(name));
        return t != null && clock.getAsLong() - t <= GRACE_MS;
    }

    @Override
    public Collection<String> names() {
        long now = clock.getAsLong();
        Collection<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : lastSeen.entrySet()) {
            if (now - e.getValue() <= GRACE_MS) out.add(display.get(e.getKey()));
        }
        return out;
    }

    /** UUID del jugador si está (o estuvo hace poco) en la lista de Tab. */
    public String uuidOf(String name) {
        return isKnown(name) ? uuids.get(Names.key(name)) : null;
    }

    /** Jugador con el nombre bien escrito (mayúsculas) y su UUID, si está o estuvo hace poco en la sala. */
    public PlayerRef find(String name) {
        if (!isKnown(name)) return null;
        String k = Names.key(name);
        return new PlayerRef(display.get(k), uuids.get(k));
    }

    /** Jugadores de la última lectura del Tab. */
    public List<PlayerRef> current() {
        return current;
    }

    public int size() {
        return current.size();
    }
}
