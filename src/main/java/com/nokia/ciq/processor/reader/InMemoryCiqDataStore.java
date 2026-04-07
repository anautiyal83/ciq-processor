package com.nokia.ciq.processor.reader;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;

import java.util.ArrayList;
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



    @Override
    public CiqIndex getIndex() {
        return index;
    }

    /**
     * Returns the in-memory sheet, or {@code null} if the table was not found in the workbook.
     *
     * <p>When {@code sheetName} is not in the main data-sheets map, falls back to the raw
     * special sheets (Index, Node_ID) so that workbook_rules and crossRef validators can
     * reference those sheets by name (e.g. {@code Index.CRGroup}).
     */
    @Override
    public CiqSheet getSheet(String sheetName) {
        CiqSheet s = sheets.get(sheetName);
        if (s != null) return s;
        if ("Index".equalsIgnoreCase(sheetName) && rawIndexSheet != null)  return rawIndexSheet;
        if ("Node_ID".equalsIgnoreCase(sheetName) && rawNodeIdSheet != null) return rawNodeIdSheet;
        return null;
    }

    @Override
    public List<String> getAvailableSheets() {
        return new ArrayList<>(sheets.keySet());
    }
}
