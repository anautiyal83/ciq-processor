package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Configuration for validation report output, read from the {@code report_output} YAML block.
 *
 * <p>All fields are optional.  When absent the processor applies the defaults shown below.
 *
 * <h3>YAML example</h3>
 * <pre>
 * report_output:
 *   formats: [JSON, HTML, MSEXCEL]                           # formats to generate (default: all three)
 *   filename: "{nodeType}_{activity}_VALIDATION_REPORT"      # base name without extension
 * </pre>
 *
 * <p>The HTML report template name and path are passed as runtime parameters
 * (e.g. CLI arguments), not as YAML config, because the template is a generic,
 * reusable file independent of node type or activity.
 *
 * <h3>Filename placeholders</h3>
 * <ul>
 *   <li>{@code \{nodeType\}} — replaced with the node type in upper case</li>
 *   <li>{@code \{activity\}} — replaced with the activity name in upper case</li>
 * </ul>
 *
 * <h3>Priority</h3>
 * <p>The CLI {@code --format} argument takes precedence over {@code formats} when both
 * are provided, so pipeline integrations can still override formats at runtime.
 */
public class ReportOutputConfig {

    /**
     * Formats to generate.  Accepted values (case-insensitive): JSON, HTML, MSEXCEL.
     * Defaults to all three when absent.
     */
    private List<String> formats;

    /**
     * Base name of the report files, without extension.
     * Supports {@code {nodeType}} and {@code {activity}} placeholders.
     * Defaults to {@code {nodeType}_{activity}_VALIDATION_REPORT} when absent.
     */
    private String filename;

    public List<String> getFormats()              { return formats; }
    public void         setFormats(List<String> v) { this.formats = v; }

    public String getFilename()               { return filename; }
    public void   setFilename(String filename) { this.filename = filename; }
}
