package com.clanstats.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Lee y guarda los archivos en config/clanstats/. */
public final class Storage {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(ClanConfig.Member.class, new ClanConfig.MemberAdapter())
            .create();
    private final Consumer<String> warn;

    public Storage(Path dir, Consumer<String> warn) {
        this.dir = dir;
        this.warn = warn;
    }

    public Path dir() {
        return dir;
    }

    public ClanConfig loadConfig() {
        Path p = dir.resolve("config.json");
        ClanConfig c = read(p, ClanConfig.class);
        if (c == null) {
            c = new ClanConfig();
        } else if (c.version < 2) {
            // config de la versión anterior (miembros solo con nombre): se guarda una copia por las dudas
            try {
                Files.copy(p, p.resolveSibling("config.v1-respaldo.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // no es grave
            }
        }
        c.sanitize();
        return c;
    }

    public Session loadSession() {
        Session s = read(dir.resolve("sesion.json"), Session.class);
        if (s == null) s = new Session();
        s.sanitize();
        return s;
    }

    public boolean saveConfig(ClanConfig c) {
        return write(dir.resolve("config.json"), gson.toJson(c));
    }

    public boolean saveSession(Session s) {
        return write(dir.resolve("sesion.json"), gson.toJson(s));
    }

    /** Guarda la sesión terminada en historial/ (texto + JSON). Devuelve la ruta del .txt. */
    public Path archive(Session s, String report) {
        String stamp = STAMP.format(LocalDateTime.now());
        Path txt = dir.resolve("historial").resolve("sesion-" + stamp + ".txt");
        write(txt, report);
        write(dir.resolve("historial").resolve("sesion-" + stamp + ".json"), gson.toJson(s));
        return txt;
    }

    public Path writeSummary(String report) {
        Path p = dir.resolve("resumen.txt");
        write(p, report);
        return p;
    }

    public Path writeDiscordSummary(String text) {
        Path p = dir.resolve("resumen-discord.txt");
        write(p, text);
        return p;
    }

    public void appendDebug(String line) {
        try {
            Files.createDirectories(dir);
            Path p = dir.resolve("debug.log");
            if (Files.exists(p) && Files.size(p) > 2_000_000) {
                Files.move(p, dir.resolve("debug.old.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(p, "[" + LOG_TIME.format(LocalDateTime.now()) + "] " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            warn.accept("No se pudo escribir debug.log: " + e.getMessage());
        }
    }

    private <T> T read(Path p, Class<T> type) {
        if (!Files.exists(p)) return null;
        try {
            return gson.fromJson(Files.readString(p, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            warn.accept("Archivo dañado " + p.getFileName() + " (" + e.getMessage() + "), se guarda una copia y se empieza de cero");
            try {
                Files.move(p, p.resolveSibling(p.getFileName() + ".danado-" + STAMP.format(LocalDateTime.now())),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // si no se puede respaldar, se sobrescribe al guardar
            }
            return null;
        }
    }

    private boolean write(Path p, String content) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            warn.accept("No se pudo guardar " + p.getFileName() + ": " + e.getMessage());
            return false;
        }
    }
}
