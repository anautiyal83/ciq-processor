package com.nokia.ciq.validator;

import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ReportFormat;

import java.io.IOException;
import java.util.List;

/**
 * Validates CIQ JSON data and produces a {@link ValidationReport}.
 *
 * <p>Usage:
 * <pre>
 *   CiqValidator validator = new CiqValidatorImpl();
 *   ValidationReport report = validator.validate(
 *       "output/json-dir",
 *       "SBC",
 *       "FIXED_LINE_CONFIGURATION",
 *       "config/SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml",
 *       "output/reports",
 *       ReportFormat.parseList("JSON,HTML,MSEXCEL"),
 *       "output/mop-json"     // optional: segregate JSON by child order when PASSED
 *   );
 *   if (!"PASSED".equals(report.getStatus())) { ... }
 * </pre>
 *
 * <p>Output files are auto-named: {@code {NODE_TYPE}_{ACTIVITY}_validation-report.{ext}}
 *
 * <p>When {@code mopJsonOutputDir} is provided and validation PASSES, per-child-order JSON
 * files are written under {@code <mopJsonOutputDir>/<NODE>_<CRGroup>/} for use by the MOP
 * generator.
 */
public interface CiqValidator {

    /**
     * Validate CIQ data and write reports in the requested formats.
     *
     * @param ciqJsonDir       directory containing JSON files from ciq-reader
     * @param nodeType         e.g. "SBC"
     * @param activity         e.g. "FIXED_LINE_CONFIGURATION"
     * @param rulesFilePath    path to the YAML validation rules file
     * @param outputDir        directory to write report files
     * @param formats          one or more output formats (JSON, HTML, MSEXCEL)
     * @param mopJsonOutputDir optional directory for per-child-order JSON segregation;
     *                         {@code null} to skip segregation
     * @return the validation report
     */
    ValidationReport validate(String ciqJsonDir, String nodeType, String activity,
                              String rulesFilePath, String outputDir,
                              List<ReportFormat> formats,
                              String mopJsonOutputDir) throws IOException;

    /**
     * Convenience overload — no JSON segregation.
     */
    default ValidationReport validate(String ciqJsonDir, String nodeType, String activity,
                                      String rulesFilePath, String outputDir,
                                      List<ReportFormat> formats) throws IOException {
        return validate(ciqJsonDir, nodeType, activity, rulesFilePath, outputDir, formats, null);
    }
}
