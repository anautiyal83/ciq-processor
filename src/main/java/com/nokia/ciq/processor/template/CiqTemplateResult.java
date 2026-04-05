package com.nokia.ciq.processor.template;

import java.util.LinkedHashMap;

/**
 * Result of a {@link CiqTemplateGenerator#generate} call.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code status}  — {@code "SUCCESS"} or {@code "FAILED"}</li>
 *   <li>{@code errors}  — number of errors (0 on success)</li>
 *   <li>{@code parameters} — additional key/value pairs, e.g. {@code CIQ_FILENAME}</li>
 * </ul>
 */
public class CiqTemplateResult {

    private final String status;
    private final int errors;
    private final LinkedHashMap<String, String> parameters;

    private CiqTemplateResult(String status, int errors, LinkedHashMap<String, String> parameters) {
        this.status     = status;
        this.errors     = errors;
        this.parameters = parameters;
    }

    public static CiqTemplateResult success(String fileName, String outputPath) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("CIQ_FILENAME", fileName);
        return new CiqTemplateResult("SUCCESS", 0, params);
    }

    public static CiqTemplateResult failure(String errorMessage) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("ERROR", errorMessage);
        return new CiqTemplateResult("FAILED", 1, params);
    }

    public String getStatus()                              { return status; }
    public int getErrors()                                 { return errors; }
    public LinkedHashMap<String, String> getParameters()  { return parameters; }
    public boolean isSuccess()                             { return "SUCCESS".equals(status); }
}
