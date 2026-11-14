package com.clanstats.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Busca el UUID de una cuenta por su nombre en la API pública de Mojang. Solo se usa cuando agregas a un
 * jugador que no está en la sala (si está, el UUID sale directo de la lista de Tab). Corre en un hilo aparte
 * para no trabar el juego.
 */
public final class MojangLookup {

    public record Profile(String name, String uuid) {}

    /** Devuelve el JSON de la cuenta, o null si no existe ninguna cuenta con ese nombre. */
    public interface Fetcher {
        String fetch(String name) throws IOException;
    }

    private static final String[] ENDPOINTS = {
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
            "https://api.mojang.com/users/profiles/minecraft/",
    };

    private final Fetcher fetcher;
    private final ExecutorService executor;
    private final Map<String, Optional<Profile>> cache = new ConcurrentHashMap<>();

    public MojangLookup() {
        this(MojangLookup::httpFetch);
    }

    public MojangLookup(Fetcher fetcher) {
        this.fetcher = fetcher;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ClanStats-UUID");
            t.setDaemon(true);
            return t;
        });
    }

    /** Termina con Optional.empty() si la cuenta no existe, o con error si no se pudo consultar. */
    public CompletableFuture<Optional<Profile>> lookup(String name) {
        String key = Names.key(name);
        Optional<Profile> hit = cache.get(key);
        if (hit != null) return CompletableFuture.completedFuture(hit);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Profile> result = parse(fetcher.fetch(name));
                cache.put(key, result);
                Thread.sleep(150); // sin apuro: no saturar la API si se agregan muchos de golpe
                return result;
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, executor);
    }

    static Optional<Profile> parse(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) return Optional.empty();
        JsonObject o = root.getAsJsonObject();
        String id = o.has("id") && o.get("id").isJsonPrimitive() ? Uuids.normalize(o.get("id").getAsString()) : null;
        String n = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : null;
        if (id == null || !Names.isValid(n)) return Optional.empty();
        return Optional.of(new Profile(n, id));
    }

    private static String httpFetch(String name) throws IOException {
        if (!Names.isValid(name)) return null;
        IOException last = null;
        for (String base : ENDPOINTS) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(base + name).toURL().openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "ClanStats-FFA (mod de Minecraft)");
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code == 204 || code == 404) return null;
                if (code == 200) {
                    try (InputStream in = c.getInputStream()) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                last = new IOException("HTTP " + code + " en " + base);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("sin respuesta");
    }
}
