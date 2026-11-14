package com.clanstats.core;

import java.util.Collection;
import java.util.List;

/** Sabe qué jugadores están (o estuvieron hace poco) en la lobby, según la lista de Tab. */
public interface NameOracle {
    /** true cuando hay una lista de jugadores confiable (2 o más nombres). */
    boolean available();

    /** El nombre está, o estuvo hace poco, en la lista de jugadores. */
    boolean isKnown(String name);

    /** Nombres conocidos ahora mismo (para la detección heurística). */
    Collection<String> names();

    NameOracle NONE = new NameOracle() {
        @Override public boolean available() { return false; }
        @Override public boolean isKnown(String name) { return false; }
        @Override public Collection<String> names() { return List.of(); }
    };
}
