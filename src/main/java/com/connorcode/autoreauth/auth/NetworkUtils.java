package com.connorcode.autoreauth.auth;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkUtils {
    public static String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String str) {
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }

    public static HttpRequest.BodyPublisher ofFormUrlEncodedData(Map<String, String> data) {
        StringBuilder builder = new StringBuilder();

        for (var entry : data.entrySet()) {
            if (!builder.isEmpty()) builder.append("&");
            builder.append(urlEncode(entry.getKey()));
            builder.append("=");
            builder.append(urlEncode(entry.getValue()));
        }

        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

    public static Map<String, String> parseQuery(String query) {
        var out = new HashMap<String, String>();

        for (var pair : query.split("&")) {
            var parts = pair.split("=");
            if (parts.length != 2) continue;
            out.put(urlDecode(parts[0]), urlDecode(parts[1]));
        }

        return out;
    }

    public static class URIBuilder {
        String base;
        List<Map.Entry<String, String>> query = new ArrayList<>();

        public URIBuilder(String base) {
            this.base = base;
        }

        public void addParameter(String key, String value) {
            this.query.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        public URI build() {
            var builder = new StringBuilder(this.base);
            if (!this.query.isEmpty()) builder.append("?");

            for (int i = 0; i < this.query.size(); i++) {
                if (i != 0) builder.append("&");
                var param = this.query.get(i);
                builder.append(urlEncode(param.getKey()));
                builder.append("=");
                builder.append(urlEncode(param.getValue()));
            }

            return URI.create(builder.toString());
        }
    }
}
