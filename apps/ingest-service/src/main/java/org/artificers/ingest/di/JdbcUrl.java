package org.artificers.ingest.di;

public final class JdbcUrl {
  private JdbcUrl() {}

  public static String from(String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("jdbc:")) {
      return url;
    }
    String normalized = url.replaceFirst("^postgres://", "postgresql://");
    return "jdbc:" + normalized;
  }
}
