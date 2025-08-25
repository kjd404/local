package com.example.poller;

final class JdbcUrl {
    private JdbcUrl() {}

    static String from(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("jdbc:")) {
            return url;
        }
        String normalized = url.replaceFirst("^postgres://", "postgresql://");
        return "jdbc:" + normalized;
    }

    static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?<=//)[^/@]+:[^@]+@", "");
    }
}
