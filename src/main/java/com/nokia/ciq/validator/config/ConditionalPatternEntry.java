package com.nokia.ciq.validator.config;

/**
 * One conditional rule within a {@link ConditionalPattern}.
 *
 * <p>The {@code when} regex is matched (full-string) against each distinct value
 * read from the lookup sheet/column.  The first matching rule wins.
 * A null or empty {@code when} acts as a catch-all default and should be placed last.
 *
 * <p>YAML example:
 * <pre>
 * rules:
 *   - when: "^13\\."
 *     pattern: "(?i).*\\.jar$"
 *     message: "Version 13.x requires a .jar file"
 *   - when: ".*"
 *     pattern: "(?i)^(?!.*\\.(jar|tar\\.gz|tgz|gz|zip|bz2|7z|rar)$).*$"
 *     message: "Plain non-compressed file required"
 * </pre>
 */
public class ConditionalPatternEntry {

    /**
     * Java regex matched (full-string) against the value read from the lookup sheet/column.
     * Null or empty acts as a catch-all — place it last in the list.
     */
    private String when;

    /**
     * Java regex the column value must satisfy (full-string match) when {@code when} matches.
     */
    private String pattern;

    /**
     * Human-readable error message when {@code pattern} does not match.
     * Supports placeholders: {@code {value}} → actual cell value,
     * {@code {lookupValue}} → the value read from the lookup sheet.
     */
    private String message;

    public String getWhen() { return when; }
    public void setWhen(String when) { this.when = when; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
