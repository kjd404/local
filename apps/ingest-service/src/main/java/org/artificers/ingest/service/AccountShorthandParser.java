package org.artificers.ingest.service;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses account shorthand strings and filenames. */
public class AccountShorthandParser {
  private static final Pattern SHORTHAND = Pattern.compile("^([A-Za-z]+)(\\d{4})$");
  private static final Pattern FILE_PATTERN = Pattern.compile("^([A-Za-z]+\\d{4}).*\\.csv$");

  /** Extracts the account shorthand from a CSV filename. */
  public String extract(Path path) {
    Matcher m = FILE_PATTERN.matcher(path.getFileName().toString());
    return m.matches() ? m.group(1).toLowerCase() : null;
  }

  /** Parses a shorthand like {@code ch1234} into its components. */
  public ParsedShorthand parse(String shorthand) {
    if (shorthand == null) throw new IllegalArgumentException("Missing account shorthand");
    Matcher m = SHORTHAND.matcher(shorthand.toLowerCase());
    if (!m.matches()) throw new IllegalArgumentException("Invalid account shorthand");
    return new ParsedShorthand(m.group(1), m.group(2));
  }

  /** Components of an account shorthand. */
  public record ParsedShorthand(String institution, String externalId) {}
}
