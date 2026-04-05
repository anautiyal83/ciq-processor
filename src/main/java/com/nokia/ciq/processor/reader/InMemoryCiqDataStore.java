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

    /** Raw USER_ID sheet rows — available for column-rule validation and CR_EMAIL_ID_LIST. */
    private CiqSheet rawUserIdSheet;

    InMemoryCiqDataStore(CiqIndex index, Map<String, CiqSheet> sheets) {
        this.index  = index;
        this.sheets = sheets;
    }

    public CiqSheet getRawIndexSheet()  { return rawIndexSheet; }
    public void     setRawIndexSheet(CiqSheet s)  { this.rawIndexSheet  = s; }

    public CiqSheet getRawNodeIdSheet() { return rawNodeIdSheet; }
    public void     setRawNodeIdSheet(CiqSheet s) { this.rawNodeIdSheet = s; }

    public CiqSheet getRawUserIdSheet() { return rawUserIdSheet; }
    public void     setRawUserIdSheet(CiqSheet s) { this.rawUserIdSheet = s; }


    @Override
    public CiqIndex getIndex() {
        return index;
    }

    /** Returns the in-memory sheet, or {@code null} if the table was not found in the workbook. */
    @Override
    public CiqSheet getSheet(String sheetName) {
        return sheets.get(sheetName);
    }

    @Override
    public List<String> getAvailableSheets() {
        return new ArrayList<>(sheets.keySet());
    }
}
