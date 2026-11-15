package org.leng.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.leng.Lengbanlist;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebResponseTest {

    private static HttpExchange exchangeWithOrigin(String origin) {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers request = new Headers();
        if (origin != null) {
            request.set("Origin", origin);
        }
        when(exchange.getRequestHeaders()).thenReturn(request);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        return exchange;
    }

    private static Lengbanlist pluginAllowing(String... origins) {
        Lengbanlist plugin = mock(Lengbanlist.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("web.allowed-origins", List.of(origins));
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    @Test
    void applyCorsHeaders_allowsConfiguredOrigin() {
        HttpExchange exchange = exchangeWithOrigin("https://panel.example.com");

        WebResponse.applyCorsHeaders(exchange, pluginAllowing("https://panel.example.com"));

        assertEquals("https://panel.example.com",
                exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin"));
        assertEquals("Content-Type, Authorization",
                exchange.getResponseHeaders().getFirst("Access-Control-Allow-Headers"));
        assertEquals("Origin", exchange.getResponseHeaders().getFirst("Vary"));
    }

    @Test
    void applyCorsHeaders_rejectsUnknownOrigin() {
        HttpExchange exchange = exchangeWithOrigin("https://evil.example.com");

        WebResponse.applyCorsHeaders(exchange, pluginAllowing("https://panel.example.com"));

        assertNull(exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin"));
        assertEquals("Origin", exchange.getResponseHeaders().getFirst("Vary"));
    }

    @Test
    void applyCorsHeaders_ignoresRequestWithoutOrigin() {
        HttpExchange exchange = exchangeWithOrigin(null);

        WebResponse.applyCorsHeaders(exchange, pluginAllowing("https://panel.example.com"));

        assertNull(exchange.getResponseHeaders().getFirst("Access-Control-Allow-Origin"));
        assertNull(exchange.getResponseHeaders().getFirst("Vary"));
    }
}
