package com.nokia.ciq.reader;

/**
 * Reads a Nokia SBC CIQ Excel file and writes JSON intermediate files
 * to the specified output directory.
 *
 * <p>Output files follow the naming convention:
 * <pre>
 *   {NODE_TYPE}_{ACTIVITY}_index.json          — Index + NIAM mapping
 *   {NODE_TYPE}_{ACTIVITY}_{SheetName}.json    — One file per table
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   CiqReader reader = new ExcelCiqReader();
 *   CiqReadResult result = reader.read("path/to/CIQ.xlsx", "output/dir", "SBC", "FIXED_LINE_CONFIGURATION");
 *   if (result.isSuccess()) System.out.println(result.getMessage());
 *   else System.err.println(result.getError());
 * </pre>
 *
 * <p>Downstream consumers (ciq-validator, mop-generator) use {@link com.nokia.ciq.reader.store.CiqDataStore}
 * to read the written JSON files one sheet at a time.
 */
public interface CiqReader {

    /**
     * Read the CIQ Excel file and write JSON files to outputDir.
     * Never throws — all outcomes are captured in the returned {@link CiqReadResult}.
     *
     * @param ciqFilePath  absolute path to the CIQ .xlsx file
     * @param outputDir    directory to write JSON output files (created if absent)
     * @param nodeType     node type identifier, e.g. "SBC" (used in file naming)
     * @param activity     activity name, e.g. "FIXED_LINE_CONFIGURATION" (used in file naming)
     * @return result with status SUCCESS or FAILED, plus message or error description
     */
    CiqReadResult read(String ciqFilePath, String outputDir, String nodeType, String activity);
}
