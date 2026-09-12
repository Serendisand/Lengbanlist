package org.leng.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class HttpHelper implements AutoCloseable {

    private final HttpClient client;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public HttpHelper(int connectTimeoutMs, int readTimeoutMs) {
        this(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs), false);
    }

    public HttpHelper(Duration connectTimeout, Duration readTimeout, boolean insecureSsl) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (insecureSsl) {
            builder.sslContext(trustAllContext());
        }
        this.client = builder.build();
    }

    public Duration getConnectTimeout() { return connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }

    public String get(String url, String userAgent) throws IOException, InterruptedException {
        return get(url, userAgent, "application/json");
    }

    public String get(String url, String userAgent, String accept) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + url);
        }
        return resp.body();
    }

    public int postJson(String url, String jsonBody, String userAgent) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return resp.statusCode();
    }

    public void download(String url, String userAgent,
                         Consumer<byte[]> chunkConsumer,
                         LongConsumer byteCounter) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout)
                .header("User-Agent", userAgent)
                .GET()
                .build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + url);
        }
        try (InputStream in = resp.body()) {
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                chunkConsumer.accept(chunk);
                total += n;
            }
            if (byteCounter != null) byteCounter.accept(total);
        }
    }

    private static SSLContext trustAllContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init insecure SSL context", e);
        }
    }

    @Override
    public void close() {

    }
}
