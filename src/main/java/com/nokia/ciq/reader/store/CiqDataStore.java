package com.nokia.ciq.reader.store;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqSheet;

import java.io.IOException;
import java.util.List;

/**
 * Read-only access to CIQ data for downstream consumers (ciq-validator, mop-generator).
 *
 * <p>Implementors read from the JSON files written by {@link com.nokia.ciq.reader.CiqReader}.
 * Only the requested sheet is loaded at a time — no full CIQ stays in memory.
 *
 * <p>Usage:
 * <pre>
 *   CiqDataStore store = new JsonFileCiqDataStore("output/dir", "SBC", "FIXED_LINE_CONFIGURATION");
 *   CiqIndex index = store.getIndex();
 *   for (String table : index.getAllTables()) {
 *       CiqSheet sheet = store.getSheet(table);
 *       // process sheet.getRows()
 *   }
 * </pre>
 */
public interface CiqDataStore {

    /**
     * Returns the parsed index (node/CRGroup/table mapping + NIAM mapping).
     */
    CiqIndex getIndex() throws IOException;

    /**
     * Returns data rows for the named table sheet.
     *
     * @param sheetName logical table name as it appears in the Index (e.g. "CRFTargetList")
     * @return sheet data, or null if no JSON file exists for this sheet
     */
    CiqSheet getSheet(String sheetName) throws IOException;

    /**
     * Returns the names of all table sheets that have a corresponding JSON file.
     */
    List<String> getAvailableSheets();
}
