package com.clanstats.core;

import java.util.ArrayList;
import java.util.List;

/** Registro de kills de la sesión actual (config/clanstats/sesion.json). */
public final class Session {

    public static final class KillEvent {
        /** Momento (epoch ms). */
        public long t;
        public String victima;
        /** null = murió sin asesino. */
        public String asesino;
        /** UUID de la víctima según la lista de Tab en ese momento (null si no se supo). */
        public String victimaUuid;
        /** UUID del asesino según la lista de Tab en ese momento (null si no se supo). */
        public String asesinoUuid;
        /** Cómo se detectó (plantilla, heurística, manual...). */
        public String fuente;
        /** Mensaje original (solo se guarda con el modo debug activo). */
        public String msg;

        public KillEvent() {}

        public KillEvent(long t, String victima, String asesino, String fuente, String msg) {
            this(t, victima, asesino, fuente, msg, null, null);
        }

        public KillEvent(long t, String victima, String asesino, String fuente, String msg,
                         String victimaUuid, String asesinoUuid) {
            this.t = t;
            this.victima = victima;
            this.asesino = asesino;
            this.fuente = fuente;
            this.msg = msg;
            this.victimaUuid = Uuids.normalize(victimaUuid);
            this.asesinoUuid = asesino == null ? null : Uuids.normalize(asesinoUuid);
        }
    }

    public long inicio = System.currentTimeMillis();
    public List<KillEvent> eventos = new ArrayList<>();

    public void sanitize() {
        if (eventos == null) eventos = new ArrayList<>();
        eventos.removeIf(e -> e == null || !Names.isValid(e.victima) || (e.asesino != null && !Names.isValid(e.asesino)));
        for (KillEvent e : eventos) {
            e.victimaUuid = Uuids.normalize(e.victimaUuid);
            e.asesinoUuid = e.asesino == null ? null : Uuids.normalize(e.asesinoUuid);
        }
        if (inicio <= 0) inicio = System.currentTimeMillis();
    }
}
