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

    InMemoryCiqDataStore(CiqIndex index, Map<String, CiqSheet> sheets) {
        this.index  = index;
        this.sheets = sheets;
    }

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
