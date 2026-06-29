package com.nokia.ciq.processor.reader;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link CiqDataStore} implementation backed entirely by in-memory objects.
 *
 * <p>Populated by {@link InMemoryExcelReader} after it parses the CIQ Excel
 * workbook. No JSON files are written or read — all data lives in the JVM heap
 * for the duration of one processing run.
 */
public class InMemoryCiqDataStore implements CiqDataStore {

    private final CiqIndex              index;
    private final Map<String, CiqSheet> sheets;

    /** Raw INDEX sheet rows — available for column-rule validation, not a data table. */
    private CiqSheet rawIndexSheet;

    /** Raw NODE_ID sheet rows — available for column-rule validation, not a data table. */
    private CiqSheet rawNodeIdSheet;

    /**
     * All sheet names present in the original Excel workbook, in workbook order.
     * Used by {@link com.nokia.ciq.validator.validator.SheetRefValidator} so that
     * {@code sheetRef: true} validates against the full workbook contents rather than
     * just the subset of sheets that were loaded as data tables.
     */
    private List<String> allWorkbookSheetNames = new ArrayList<>();

    /**
     * Maps logical (full) table names to actual Excel sheet names.
     * Populated from {@code sheet_aliases} in the validation-rules YAML.
     * Used as a last-resort fallback in {@link #getSheet(String)} when the requested
     * name does not match any loaded sheet directly.
     */
    private Map<String, String> sheetAliases = Collections.emptyMap();


    InMemoryCiqDataStore(CiqIndex index, Map<String, CiqSheet> sheets) {
        this.index  = index;
        this.sheets = sheets;
    }

    public CiqSheet getRawIndexSheet()  { return rawIndexSheet; }
    public void     setRawIndexSheet(CiqSheet s)  { this.rawIndexSheet  = s; }

    public CiqSheet getRawNodeIdSheet() { return rawNodeIdSheet; }
    public void     setRawNodeIdSheet(CiqSheet s) { this.rawNodeIdSheet = s; }

    public List<String> getAllWorkbookSheetNames() { return allWorkbookSheetNames; }
    public void setAllWorkbookSheetNames(List<String> names) { this.allWorkbookSheetNames = names; }

    public Map<String, String> getSheetAliases()                           { return sheetAliases; }
    public void                setSheetAliases(Map<String, String> aliases) {
        this.sheetAliases = aliases != null ? aliases : Collections.emptyMap();
    }



    @Override
    public CiqIndex getIndex() {
        return index;
    }

    /**
     * Returns the in-memory sheet, or {@code null} if the table was not found in the workbook.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match in data-sheets map.</li>
     *   <li>Case-insensitive match in data-sheets map.</li>
     *   <li>Special sheets: Index, Node_ID.</li>
     *   <li>Alias lookup via {@code sheet_aliases} — resolves a logical full name to the
     *       actual (possibly truncated) Excel sheet name, then repeats steps 1–2.</li>
     * </ol>
     */
    @Override
    public CiqSheet getSheet(String sheetName) {
        // Exact match first
        CiqSheet s = sheets.get(sheetName);
        if (s != null) return s;
        // Case-insensitive fallback over data sheets
        for (Map.Entry<String, CiqSheet> e : sheets.entrySet()) {
            if (e.getKey().equalsIgnoreCase(sheetName)) return e.getValue();
        }
        // Special sheets stored outside the main map
        if ("Index".equalsIgnoreCase(sheetName) && rawIndexSheet != null)  return rawIndexSheet;
        if ("Node_ID".equalsIgnoreCase(sheetName) && rawNodeIdSheet != null) return rawNodeIdSheet;
        // Alias lookup: logical name → actual sheet name (handles Excel 31-char truncation)
        String aliased = sheetAliases.get(sheetName);
        if (aliased == null) {
            // Case-insensitive alias lookup
            for (Map.Entry<String, String> e : sheetAliases.entrySet()) {
                if (e.getKey().equalsIgnoreCase(sheetName)) { aliased = e.getValue(); break; }
            }
        }
        if (aliased != null) {
            s = sheets.get(aliased);
            if (s != null) return s;
            for (Map.Entry<String, CiqSheet> e : sheets.entrySet()) {
                if (e.getKey().equalsIgnoreCase(aliased)) return e.getValue();
            }
        }
        return null;
    }

    @Override
    public List<String> getAvailableSheets() {
        return new ArrayList<>(sheets.keySet());
    }
}
