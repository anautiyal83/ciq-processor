package com.nokia.ciq.processor;

/**
 * Controls whether segregation produces one JSON per unit or one JSON for the whole workbook.
 *
 * <p>Set via {@code output_mode:} in the validation-rules YAML.
 * When absent, defaults to {@link #INDIVIDUAL}.
 *
 * <ul>
 *   <li>{@code individual} — one JSON per segregation unit (CR / Group / Node),
 *       auto-detected from the workbook structure.</li>
 *   <li>{@code single} — one JSON for the entire workbook.</li>
 * </ul>
 */
public enum OutputMode {

    /** One JSON per segregation unit — auto-detected (default). */
    INDIVIDUAL,

    /** One JSON for the entire workbook. */
    SINGLE;

    public static OutputMode fromString(String s) {
        if (s == null) return INDIVIDUAL;
        switch (s.trim().toLowerCase()) {
            case "individual": return INDIVIDUAL;
            case "single":     return SINGLE;
            default:
                throw new IllegalArgumentException(
                        "Unknown output_mode: '" + s + "'. Allowed: individual | single");
        }
    }
}
