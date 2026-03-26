package com.nokia.ciq.reader.util;

/**
 * Generates consistent file names for CIQ JSON output files.
 *
 * <p>Two naming conventions are supported:
 * <ul>
 *   <li><b>Global</b> (no child order): {@code {NODE_TYPE}_{ACTIVITY}_{suffix}.json}
 *       — written once, contains all nodes/rows.</li>
 *   <li><b>Child-order</b>: {@code {NODE_TYPE}_{ACTIVITY}_{suffix}_{childOrder}.json}
 *       — one set per (Node_CRGroup) combination, rows filtered to that node.</li>
 * </ul>
 */
public class FileNamingUtil {

    private FileNamingUtil() {}

    // -------------------------------------------------------------------------
    // Global (no child order)
    // -------------------------------------------------------------------------

    /**
     * Index file name (global).
     * Example: {@code SBC_FIXED_LINE_CONFIGURATION_index.json}
     */
    public static String indexFileName(String nodeType, String activity) {
        return prefix(nodeType, activity) + "index.json";
    }

    /**
     * Sheet file name (global).
     * Example: {@code SBC_FIXED_LINE_CONFIGURATION_CRFTargetList.json}
     */
    public static String sheetFileName(String nodeType, String activity, String sheetName) {
        return prefix(nodeType, activity) + sheetName + ".json";
    }

    /** Returns the common file name prefix, e.g. {@code SBC_FIXED_LINE_CONFIGURATION_}. */
    public static String sheetFilePrefix(String nodeType, String activity) {
        return prefix(nodeType, activity);
    }

    // -------------------------------------------------------------------------
    // Child-order (postfixed with _<Node>_<CRGroup>)
    // -------------------------------------------------------------------------

    /**
     * Index file name for a specific child order.
     * Example: {@code SBC_FIXED_LINE_CONFIGURATION_index_SBC-1_CR1.json}
     */
    public static String indexFileName(String nodeType, String activity, String childOrder) {
        return prefix(nodeType, activity) + "index_" + childOrder + ".json";
    }

    /**
     * Sheet file name for a specific child order.
     * Example: {@code SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_SBC-1_CR1.json}
     */
    public static String sheetFileName(String nodeType, String activity,
                                       String sheetName, String childOrder) {
        return prefix(nodeType, activity) + sheetName + "_" + childOrder + ".json";
    }

    /**
     * File name suffix appended to every file in a child-order folder.
     * Example: {@code _SBC-1_CR1.json}
     */
    public static String childOrderSuffix(String childOrder) {
        return "_" + childOrder + ".json";
    }

    private static String prefix(String nodeType, String activity) {
        return nodeType.toUpperCase() + "_" + activity.toUpperCase() + "_";
    }
}
