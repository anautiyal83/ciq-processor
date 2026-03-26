package com.nokia.ciq.reader.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.util.FileNamingUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link CiqDataStore} backed by the JSON files written by {@link com.nokia.ciq.reader.ExcelCiqReader}.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Global</b>: {@code new JsonFileCiqDataStore(dir, nodeType, activity)}
 *       — reads global (all-node) JSON files.</li>
 *   <li><b>Child-order</b>: {@code new JsonFileCiqDataStore(dir, nodeType, activity, childOrder)}
 *       — reads child-order-specific files (postfixed with {@code _<childOrder>})
 *       written by the validator's segregation step.</li>
 * </ul>
 */
public class JsonFileCiqDataStore implements CiqDataStore {

    private final String outputDir;
    private final String nodeType;
    private final String activity;
    /** Non-null when reading child-order-specific files (e.g. "SBC-1_CR1"). */
    private final String childOrder;
    private final ObjectMapper mapper;

    /** Global mode — reads files without child-order postfix. */
    public JsonFileCiqDataStore(String outputDir, String nodeType, String activity) {
        this(outputDir, nodeType, activity, null);
    }

    /**
     * Child-order mode — reads files postfixed with {@code _<childOrder>}.
     *
     * @param childOrder e.g. {@code "SBC-1_CR1"} (Node_CRGroup)
     */
    public JsonFileCiqDataStore(String outputDir, String nodeType,
                                String activity, String childOrder) {
        this.outputDir  = outputDir;
        this.nodeType   = nodeType;
        this.activity   = activity;
        this.childOrder = childOrder;
        this.mapper     = new ObjectMapper();
    }

    @Override
    public CiqIndex getIndex() throws IOException {
        File file = indexFile();
        if (!file.exists()) {
            throw new IOException("Index file not found: " + file.getAbsolutePath());
        }
        return mapper.readValue(file, CiqIndex.class);
    }

    @Override
    public CiqSheet getSheet(String sheetName) throws IOException {
        File file = sheetFile(sheetName);
        if (!file.exists()) return null;
        return mapper.readValue(file, CiqSheet.class);
    }

    @Override
    public List<String> getAvailableSheets() {
        List<String> names = new ArrayList<>();
        File dir = new File(outputDir);
        if (!dir.exists()) return names;

        File[] files = dir.listFiles();
        if (files == null) return names;

        String prefix = FileNamingUtil.sheetFilePrefix(nodeType, activity);
        String suffix = (childOrder != null)
                ? FileNamingUtil.childOrderSuffix(childOrder)   // e.g. "_SBC-1_CR1.json"
                : ".json";
        String indexName = indexFile().getName();

        for (File f : files) {
            String fname = f.getName();
            if (fname.equals(indexName)) continue;
            if (fname.startsWith(prefix) && fname.endsWith(suffix)) {
                // strip leading prefix and trailing suffix to get the bare sheet name
                String sheetName = fname.substring(prefix.length(),
                        fname.length() - suffix.length());
                names.add(sheetName);
            }
        }
        return names;
    }

    private File indexFile() {
        return childOrder != null
                ? new File(outputDir, FileNamingUtil.indexFileName(nodeType, activity, childOrder))
                : new File(outputDir, FileNamingUtil.indexFileName(nodeType, activity));
    }

    private File sheetFile(String sheetName) {
        return childOrder != null
                ? new File(outputDir, FileNamingUtil.sheetFileName(nodeType, activity, sheetName, childOrder))
                : new File(outputDir, FileNamingUtil.sheetFileName(nodeType, activity, sheetName));
    }
}
