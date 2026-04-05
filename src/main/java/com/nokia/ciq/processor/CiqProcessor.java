package com.nokia.ciq.processor;

import com.nokia.ciq.validator.model.ValidationReport;

import java.io.IOException;

/**
 * Contract for the CIQ Processor.
 *
 * <p>Reads a CIQ Excel file directly into memory, validates it against
 * YAML-defined rules, and writes reports in the requested formats — all in a
 * single pass with no intermediate JSON files on disk.
 *
 * <p>Output is identical to {@code CiqValidatorImpl} from the ciq-validator
 * library: JSON, HTML, and/or Excel validation reports, plus optional
 * child-order–segregated JSON files for MOP generation.
 */
public interface CiqProcessor {

    /**
     * Process a CIQ Excel file end-to-end.
     *
     * @param ciqFilePath      absolute path to the .xlsx CIQ workbook
     * @param nodeType         node type, e.g. {@code "SBC"}
     * @param activity         activity name, e.g. {@code "FIXED_LINE_CONFIGURATION"}
     * @param rulesFilePath    absolute path to the YAML validation-rules file
     * @param outputDir        directory where validation reports are written
     * @param formatCsv        comma-separated report formats (JSON, HTML, MSEXCEL);
     *                         {@code null} defaults to all three
     * @param mopJsonOutputDir optional directory for child-order–segregated JSON files;
     *                         written only when validation passes; {@code null} = skip
     * @return the completed {@link ValidationReport}
     * @throws IOException if the Excel file or rules file cannot be read,
     *                     or if any report file cannot be written
     */
    ValidationReport process(String ciqFilePath,
                             String nodeType,
                             String activity,
                             String rulesFilePath,
                             String outputDir,
                             String formatCsv,
                             String mopJsonOutputDir) throws IOException;
}
