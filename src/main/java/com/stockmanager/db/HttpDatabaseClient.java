package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpDatabaseClient {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson gson = new Gson();
    
    private static String backendUrl = "http://localhost:3000"; // default fallback
    private static String jwtToken = null;

    public static void setBackendUrl(String url) {
        if (url != null && !url.isBlank()) {
            if (url.endsWith("/")) {
                backendUrl = url.substring(0, url.length() - 1);
            } else {
                backendUrl = url;
            }
        }
    }

    public static String getBackendUrl() {
        return backendUrl;
    }

    public static void setJwtToken(String token) {
        jwtToken = token;
    }

    public static String getJwtToken() {
        return jwtToken;
    }

    public static boolean hasToken() {
        return jwtToken != null && !jwtToken.isBlank();
    }

    // ─── HTTP GET Request ────────────────────────────────────────────────────
    public static HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(15))
                .GET();

        if (hasToken()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpRequest request = builder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ─── HTTP POST Request ───────────────────────────────────────────────────
    public static HttpResponse<String> post(String path, Object body) throws Exception {
        String jsonPayload = body instanceof String ? (String) body : gson.toJson(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

        if (hasToken()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpRequest request = builder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ─── HTTP PUT Request ────────────────────────────────────────────────────
    public static HttpResponse<String> put(String path, Object body) throws Exception {
        String jsonPayload = body instanceof String ? (String) body : gson.toJson(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload));

        if (hasToken()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpRequest request = builder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ─── HTTP DELETE Request ─────────────────────────────────────────────────
    public static HttpResponse<String> delete(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(15))
                .DELETE();

        if (hasToken()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }

        HttpRequest request = builder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ─── Helper method to parse errors from JSON response ───────────────────
    public static String getErrorMessage(String responseBody, String defaultMsg) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {}
        return defaultMsg;
    }
}
