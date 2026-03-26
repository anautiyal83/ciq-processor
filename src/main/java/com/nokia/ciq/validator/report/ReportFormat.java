package com.nokia.ciq.validator.report;

/**
 * Supported validation report output formats.
 * Multiple formats can be requested in a single run.
 */
public enum ReportFormat {
    JSON,
    HTML,
    MSEXCEL;

    /** Parse a comma-separated list, e.g. "JSON,HTML,MSEXCEL". */
    public static java.util.List<ReportFormat> parseList(String csv) {
        java.util.List<ReportFormat> list = new java.util.ArrayList<>();
        for (String token : csv.split(",")) {
            String t = token.trim().toUpperCase();
            if (!t.isEmpty()) {
                try {
                    list.add(ReportFormat.valueOf(t));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Unknown report format: '" + token.trim() +
                            "'. Valid values: JSON, HTML, MSEXCEL");
                }
            }
        }
        if (list.isEmpty()) throw new IllegalArgumentException("No report formats specified");
        return list;
    }

    /** Returns the file extension for this format. */
    public String extension() {
        switch (this) {
            case JSON:    return "json";
            case HTML:    return "html";
            case MSEXCEL: return "xlsx";
            default:      return name().toLowerCase();
        }
    }
}
