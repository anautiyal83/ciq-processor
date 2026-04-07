package com.nokia.ciq.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nokia.ciq.processor.model.CRGroupIndex;
import com.nokia.ciq.processor.model.GroupIndex;
import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.processor.reader.InMemoryExcelReader;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.model.NodeEntry;
import com.nokia.ciq.reader.util.FileNamingUtil;
import com.nokia.ciq.validator.CiqValidationEngine;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.config.ValidationRulesLoader;
import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ExcelReportWriter;
import com.nokia.ciq.validator.report.HtmlReportWriter;
import com.nokia.ciq.validator.report.ReportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link CiqProcessor}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link ValidationRulesLoader} loads YAML validation rules (first, so that the
 *       NODE_ID sheet name and column names are available for the reader).</li>
 *   <li>{@link InMemoryExcelReader} reads the CIQ Excel workbook into an
 *       {@link InMemoryCiqDataStore} using the configured NODE_ID sheet settings — no JSON files written.</li>
 *   <li>{@link CiqValidationEngine} validates the in-memory data.</li>
 *   <li>Reports are written in the requested formats (JSON, HTML, Excel).</li>
 *   <li>If validation passed and {@code mopJsonOutputDir} is provided, child-order–
 *       segregated JSON files are written for MOP generation.</li>
 * </ol>
 *
 * <p>The report format and child-order JSON layout are identical to those
 * produced by {@code CiqValidatorImpl} from the ciq-validator library.
 */
public class CiqProcessorImpl implements CiqProcessor {

    private static final Logger log = LoggerFactory.getLogger(CiqProcessorImpl.class);

    private final ObjectMapper mapper;

    public CiqProcessorImpl() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public ValidationReport process(String ciqFilePath,
                                    String nodeType,
                                    String activity,
                                    String rulesFilePath,
                                    String outputDir,
                                    String formatCsv,
                                    String mopJsonOutputDir) throws IOException {

        List<ReportFormat> formats = ReportFormat.parseList(
                formatCsv != null ? formatCsv : "JSON,HTML,MSEXCEL");

        log.info("=== CIQ Processor ===");
        log.info("CIQ file:  {}", ciqFilePath);
        log.info("Node type: {}, Activity: {}", nodeType, activity);
        log.info("Rules:     {}", rulesFilePath);
        log.info("Formats:   {}", formats);

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        // Step 1: Load validation rules (needed first to configure NODE_ID sheet reading)
        ValidationRulesConfig rules = new ValidationRulesLoader().load(rulesFilePath);

        // Step 2: Read Excel into memory using configured NODE_ID sheet name and columns
        InMemoryExcelReader reader = new InMemoryExcelReader();
        InMemoryCiqDataStore store = reader.read(ciqFilePath, nodeType, activity, rules);

        // Step 3: Validate
        ValidationReport report =
                new CiqValidationEngine(store, rules).validate(nodeType, activity);

        // Step 4: Write reports
        String baseName = nodeType.toUpperCase() + "_" + activity.toUpperCase() + "_VALIDATION_REPORT";
        report.getParameters().put("REPORT_FILENAME", outputDir + "/" + baseName);
        for (ReportFormat fmt : formats) {
            String path = new File(outDir, baseName + "." + fmt.extension()).getAbsolutePath();
            switch (fmt) {
                case JSON:
                    mapper.writeValue(new File(path), report);
                    log.info("JSON report:  {}", path);
                    break;
                case HTML:
                    new HtmlReportWriter().write(report, path);
                    log.info("HTML report:  {}", path);
                    break;
                case MSEXCEL:
                    new ExcelReportWriter().write(report, path);
                    log.info("Excel report: {}", path);
                    break;
            }
        }

        // Step 5: JSON segregation (only on PASSED)
        if ("PASSED".equals(report.getStatus()) && mopJsonOutputDir != null) {
            String groupByCol   = rules.getGroupByColumnName();
            boolean forceNode   = groupByCol != null && groupByCol.equalsIgnoreCase("NODE");
            boolean forceGroup  = groupByCol != null && groupByCol.equalsIgnoreCase("GROUP");
            boolean forceCRGroup = groupByCol != null && groupByCol.equalsIgnoreCase("CRGROUP");

            Map<String, Map<String, List<String>>> crGroupData = reader.getCrGroupToGroupNodes();
            Map<String, List<String>> groupToNodes = reader.getGroupToNodes();

            if (!forceNode && !forceGroup && (forceCRGroup || !crGroupData.isEmpty())) {
                // CRGROUP mode: explicitly requested via groupByColumnName=CRGROUP,
                // or auto-detected because INDEX has GROUP | CRGROUP | NODE columns.
                // Suppressed when groupByColumnName=GROUP (forceGroup overrides auto-detection).
                log.info("CRGROUP mode (groupByColumnName={}) — segregating into CRGROUP folders: {}",
                        groupByCol != null ? groupByCol : "auto", mopJsonOutputDir);
                segregateCRGroupBased(store, crGroupData, nodeType, activity, mopJsonOutputDir, rules);
            } else {
                boolean useGroupMode = forceGroup || (!forceNode && !groupToNodes.isEmpty());
                if (useGroupMode && !groupToNodes.isEmpty()) {
                    log.info("GROUP mode (groupByColumnName={}) — segregating into group folders: {}",
                            groupByCol != null ? groupByCol : "auto", mopJsonOutputDir);
                    segregateGroupBased(store, groupToNodes, nodeType, activity, mopJsonOutputDir);
                } else {
                    log.info("NODE mode (groupByColumnName={}) — segregating by child order into: {}",
                            groupByCol != null ? groupByCol : "auto", mopJsonOutputDir);
                    segregateByChildOrder(store, nodeType, activity, mopJsonOutputDir);
                }
            }
        }

        return report;
    }

    // -------------------------------------------------------------------------
    // Child-order JSON segregation
    // -------------------------------------------------------------------------

    /**
     * For each (Node, CRGroup) combination in the index, writes a subfolder
     * {@code <mopJsonOutputDir>/<NODE>_<CRGroup>/} containing:
     * <ul>
     *   <li>A filtered index JSON with only that child order's entry</li>
     *   <li>Per-table JSON files with rows filtered to {@code Node == entry.getNode()}</li>
     * </ul>
     *
     * <p>File names use the child-order postfix convention from {@link FileNamingUtil}.
     * The output is identical to the segregation produced by {@code CiqValidatorImpl}.
     */
    private void segregateByChildOrder(InMemoryCiqDataStore store,
                                       String nodeType,
                                       String activity,
                                       String mopJsonOutputDir) throws IOException {

        CiqIndex index = store.getIndex();

        // No Index sheet — derive child orders from Node column in data sheets
        if (index.getEntries().isEmpty()) {
            segregateNoIndex(store, nodeType, activity, mopJsonOutputDir);
            return;
        }

        for (NodeEntry entry : index.getEntries()) {
            String node    = entry.getNode()    != null ? entry.getNode().trim()    : "";
            String crGroup = entry.getCrGroup() != null ? entry.getCrGroup().trim() : "";
            if (node.isEmpty()) continue;

            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;

            File childDir = new File(mopJsonOutputDir, childOrder);
            if (!childDir.exists()) childDir.mkdirs();

            // Filtered index (single entry for this child order)
            Map<String, String> niamSubset = new LinkedHashMap<>();
            String niamId = index.getNiamMapping().get(node);
            if (niamId != null) niamSubset.put(node, niamId);

            CiqIndex childIndex = new CiqIndex();
            childIndex.setNodeType(nodeType);
            childIndex.setActivity(activity);
            childIndex.getEntries().add(entry);
            childIndex.setNiamMapping(niamSubset);

            String indexFileName = FileNamingUtil.indexFileName(nodeType, activity, childOrder);
            mapper.writeValue(new File(childDir, indexFileName), childIndex);
            log.info("  [{}] index → {}", childOrder, indexFileName);

            // Per-table JSONs filtered to this node
            for (String tableName : entry.getTables()) {
                CiqSheet sheet = store.getSheet(tableName);
                if (sheet == null) {
                    log.warn("  [{}] table '{}' not found — skipping", childOrder, tableName);
                    continue;
                }

                CiqSheet filteredSheet = new CiqSheet();
                filteredSheet.setSheetName(sheet.getSheetName());
                filteredSheet.setColumns(sheet.getColumns());
                List<CiqRow> filteredRows = new ArrayList<>();
                for (CiqRow row : sheet.getRows()) {
                    if (node.equals(row.get("Node"))) {
                        filteredRows.add(row);
                    }
                }
                filteredSheet.setRows(filteredRows);

                String sheetFileName =
                        FileNamingUtil.sheetFileName(nodeType, activity, tableName, childOrder);
                mapper.writeValue(new File(childDir, sheetFileName), filteredSheet);
                log.info("  [{}] table '{}' ({} rows) → {}",
                        childOrder, tableName, filteredRows.size(), sheetFileName);
            }
        }
    }

    // -------------------------------------------------------------------------
    // CRGROUP-mode JSON segregation
    // -------------------------------------------------------------------------

    /**
     * CRGROUP-mode segregation: for each unique CRGROUP value, writes a subfolder
     * {@code <mopJsonOutputDir>/<CRGROUP>/} containing:
     * <ul>
     *   <li>A {@link CRGroupIndex} JSON with every GROUP that participates in this CR,
     *       the node list per GROUP, and the NIAM sub-mapping</li>
     *   <li>Per-GROUP data JSON files (rows filtered by GROUP name, GROUP used as postfix)
     *       for every data sheet</li>
     * </ul>
     *
     * <p>A GROUP may appear in multiple CRGROUP folders — its data rows are identical
     * (homogeneous) across all CRs, so the same filtered JSON is written to each.
     */
    private void segregateCRGroupBased(InMemoryCiqDataStore store,
                                       Map<String, Map<String, List<String>>> crGroupToGroupNodes,
                                       String nodeType,
                                       String activity,
                                       String mopJsonOutputDir,
                                       ValidationRulesConfig rules) throws IOException {

        CiqIndex index = store.getIndex();

        List<String> dataSheets = new ArrayList<>();
        for (String name : store.getAvailableSheets()) {
            if (!name.replace("_", "").equalsIgnoreCase("index")) {
                dataSheets.add(name);
            }
        }

        // Build CRGROUP → email map from User-ID sheet
        Map<String, String> crGroupToEmail = new LinkedHashMap<>();
        String userIdSheetName = rules != null ? rules.getUserIdSheetName() : null;
        if (userIdSheetName != null && store.getRawUserIdSheet() != null) {
            com.nokia.ciq.validator.config.SheetRules userIdRules =
                    (rules.getSheets() != null) ? rules.getSheets().get(userIdSheetName) : null;
            String crGroupCol = (userIdRules != null && userIdRules.getCrGroupColumn() != null)
                    ? userIdRules.getCrGroupColumn() : "CRGROUP";
            String emailCol   = (userIdRules != null && userIdRules.getEmailColumn() != null)
                    ? userIdRules.getEmailColumn() : "EMAIL";
            for (CiqRow row : store.getRawUserIdSheet().getRows()) {
                String crg   = row.get(crGroupCol);
                String email = row.get(emailCol);
                if (crg != null && !crg.trim().isEmpty() && email != null && !email.trim().isEmpty()) {
                    crGroupToEmail.put(crg.trim(), email.trim());
                }
            }
        }

        for (Map.Entry<String, Map<String, List<String>>> crEntry : crGroupToGroupNodes.entrySet()) {
            String crGroup              = crEntry.getKey();
            Map<String, List<String>> groupToNodes = crEntry.getValue();

            File crGroupDir = new File(mopJsonOutputDir, crGroup);
            if (!crGroupDir.exists()) crGroupDir.mkdirs();

            // Build CRGroupIndex
            CRGroupIndex crGroupIndex = new CRGroupIndex();
            crGroupIndex.setNodeType(nodeType);
            crGroupIndex.setActivity(activity);
            crGroupIndex.setCrGroup(crGroup);
            crGroupIndex.setEmail(crGroupToEmail.get(crGroup));

            List<CRGroupIndex.GroupEntry> groupEntries = new ArrayList<>();

            for (Map.Entry<String, List<String>> gEntry : groupToNodes.entrySet()) {
                String group          = gEntry.getKey();
                List<String> nodeList = gEntry.getValue();

                // NIAM subset for this group's nodes
                Map<String, String> niamSubset = new LinkedHashMap<>();
                for (String node : nodeList) {
                    String neid = index.getNiamMapping().get(node);
                    if (neid != null) niamSubset.put(node, neid);
                }

                // Collect table rows for this group directly into tableData (no separate files)
                Map<String, List<Map<String, String>>> tableData = new LinkedHashMap<>();
                for (String tableName : dataSheets) {
                    CiqSheet sheet = store.getSheet(tableName);
                    if (sheet == null) {
                        log.warn("  [{}/{}] table '{}' not found — skipping", crGroup, group, tableName);
                        continue;
                    }
                    List<Map<String, String>> rows = new ArrayList<>();
                    for (CiqRow row : sheet.getRows()) {
                        if (group.equals(row.get("Group"))) rows.add(row.getData());
                    }
                    tableData.put(tableName, rows);
                    log.info("  [{}/{}] table '{}' — {} row(s) embedded", crGroup, group, tableName, rows.size());
                }

                CRGroupIndex.GroupEntry ge = new CRGroupIndex.GroupEntry();
                ge.setGroup(group);
                ge.setNodes(nodeList);
                ge.setNiamMapping(niamSubset);
                ge.setTableData(tableData);
                groupEntries.add(ge);
            }

            crGroupIndex.setGroups(groupEntries);

            String indexFileName = FileNamingUtil.indexFileName(nodeType, activity, crGroup);
            mapper.writeValue(new File(crGroupDir, indexFileName), crGroupIndex);
            log.info("CRGROUP {} → groups {}, {} data sheet(s)",
                    crGroup, groupToNodes.keySet(), dataSheets.size());
        }
    }

    /**
     * GROUP-mode segregation: for each group, writes a subfolder
     * {@code <mopJsonOutputDir>/<GROUP>/} containing:
     * <ul>
     *   <li>A {@link GroupIndex} JSON with the group's nodes, tables, and NIAM subset</li>
     *   <li>Per-table JSON files with rows filtered to {@code Group == groupName}</li>
     * </ul>
     */
    private void segregateGroupBased(InMemoryCiqDataStore store,
                                     Map<String, List<String>> groupToNodes,
                                     String nodeType,
                                     String activity,
                                     String mopJsonOutputDir) throws IOException {

        CiqIndex index = store.getIndex();

        // Data sheets — all sheets except INDEX
        List<String> dataSheets = new ArrayList<>();
        for (String name : store.getAvailableSheets()) {
            if (!name.replace("_", "").equalsIgnoreCase("index")) {
                dataSheets.add(name);
            }
        }

        for (Map.Entry<String, List<String>> entry : groupToNodes.entrySet()) {
            String group    = entry.getKey();
            List<String> nodeList = entry.getValue();

            File groupDir = new File(mopJsonOutputDir, group);
            if (!groupDir.exists()) groupDir.mkdirs();

            // Per-table JSONs filtered to this group
            for (String tableName : dataSheets) {
                CiqSheet sheet = store.getSheet(tableName);
                if (sheet == null) {
                    log.warn("  [{}] table '{}' not found — skipping", group, tableName);
                    continue;
                }

                CiqSheet filteredSheet = new CiqSheet();
                filteredSheet.setSheetName(sheet.getSheetName());
                filteredSheet.setColumns(sheet.getColumns());
                List<CiqRow> filteredRows = new ArrayList<>();
                for (CiqRow row : sheet.getRows()) {
                    String rowGroup = row.get("Group");
                    if (group.equals(rowGroup)) {
                        filteredRows.add(row);
                    }
                }
                filteredSheet.setRows(filteredRows);

                String sheetFileName =
                        FileNamingUtil.sheetFileName(nodeType, activity, tableName, group);
                mapper.writeValue(new File(groupDir, sheetFileName), filteredSheet);
                log.info("  [{}] table '{}' ({} rows) → {}",
                        group, tableName, filteredRows.size(), sheetFileName);
            }

            // NIAM subset for this group's nodes
            Map<String, String> niamSubset = new LinkedHashMap<>();
            for (String node : nodeList) {
                String neid = index.getNiamMapping().get(node);
                if (neid != null) niamSubset.put(node, neid);
            }

            // GroupIndex JSON
            GroupIndex groupIndex = new GroupIndex();
            groupIndex.setNodeType(nodeType);
            groupIndex.setActivity(activity);
            groupIndex.setGroup(group);
            groupIndex.setNodes(nodeList);
            groupIndex.setTables(new ArrayList<>(dataSheets));
            groupIndex.setNiamMapping(niamSubset);

            String indexFileName = FileNamingUtil.indexFileName(nodeType, activity, group);
            mapper.writeValue(new File(groupDir, indexFileName), groupIndex);
            log.info("Group {} → nodes {}, {} data sheet(s)", group, nodeList, dataSheets.size());
        }
    }

    /**
     * Fallback segregation for CIQs that have no proper Index sheet (no Tables column).
     *
     * <p>Grouping strategy:
     * <ul>
     *   <li>INDEX sheet present → nodes and CRGroup sourced from INDEX → folder {@code NODE_CRGROUP}</li>
     *   <li>INDEX sheet absent  → nodes sourced from NODE_ID sheet     → folder {@code NODE}</li>
     * </ul>
     *
     * <p>The INDEX sheet is used solely for grouping metadata and is excluded from the
     * per-child-order data files written to disk.
     */
    private void segregateNoIndex(InMemoryCiqDataStore store,
                                  String nodeType,
                                  String activity,
                                  String mopJsonOutputDir) throws IOException {

        // Data sheets — exclude INDEX (grouping metadata only)
        List<String> dataSheets = new ArrayList<>();
        for (String name : store.getAvailableSheets()) {
            if (!name.replace("_", "").equalsIgnoreCase("index")) {
                dataSheets.add(name);
            }
        }

        // Determine grouping source
        Map<String, String> nodeToCrGroup = buildNodeCrGroupMap(store);
        boolean indexSheetPresent = !nodeToCrGroup.isEmpty();

        Set<String> nodes;
        if (indexSheetPresent) {
            // INDEX present → node list comes from INDEX sheet (preserves INDEX row order)
            nodes = new LinkedHashSet<>(nodeToCrGroup.keySet());
            log.info("INDEX sheet present — grouping {} node(s) by NODE + CRGroup: {}",
                    nodes.size(), nodes);
        } else {
            // INDEX absent → node list comes from NODE_ID sheet
            nodes = new LinkedHashSet<>(store.getIndex().getNiamMapping().keySet());
            log.info("INDEX sheet absent — grouping {} node(s) by NODE (from NODE_ID): {}",
                    nodes.size(), nodes);
        }

        if (nodes.isEmpty()) {
            log.warn("No nodes found for child-order segregation — skipped");
            return;
        }

        for (String node : nodes) {
            String crGroup    = nodeToCrGroup.getOrDefault(node, "").trim();
            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;

            File childDir = new File(mopJsonOutputDir, childOrder);
            if (!childDir.exists()) childDir.mkdirs();

            // Synthetic index entry
            NodeEntry entry = new NodeEntry();
            entry.setNode(node);
            entry.setCrGroup(crGroup);
            entry.setTables(new ArrayList<>(dataSheets));

            CiqIndex childIndex = new CiqIndex();
            childIndex.setNodeType(nodeType);
            childIndex.setActivity(activity);
            childIndex.getEntries().add(entry);
            childIndex.setNiamMapping(store.getIndex().getNiamMapping());

            String indexFileName = FileNamingUtil.indexFileName(nodeType, activity, childOrder);
            mapper.writeValue(new File(childDir, indexFileName), childIndex);
            log.info("  [{}] index → {}", childOrder, indexFileName);

            // Per-table JSONs filtered to this node
            for (String tableName : dataSheets) {
                CiqSheet sheet = store.getSheet(tableName);
                if (sheet == null) continue;

                CiqSheet filtered = new CiqSheet();
                filtered.setSheetName(sheet.getSheetName());
                filtered.setColumns(sheet.getColumns());

                List<CiqRow> filteredRows = new ArrayList<>();
                for (CiqRow row : sheet.getRows()) {
                    if (node.equals(row.get("Node"))) filteredRows.add(row);
                }
                filtered.setRows(filteredRows);

                String sheetFileName =
                        FileNamingUtil.sheetFileName(nodeType, activity, tableName, childOrder);
                mapper.writeValue(new File(childDir, sheetFileName), filtered);
                log.info("  [{}] table '{}' ({} rows) → {}",
                        childOrder, tableName, filteredRows.size(), sheetFileName);
            }
        }
    }

    /**
     * Reads the INDEX data sheet (if present in the store) and returns a map of
     * node name → CRGroup value.  Lookup is case-insensitive and underscore-insensitive
     * so "INDEX", "Index", and "index" all match.
     */
    private Map<String, String> buildNodeCrGroupMap(InMemoryCiqDataStore store) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String sheetName : store.getAvailableSheets()) {
            if (sheetName.replace("_", "").equalsIgnoreCase("index")) {
                CiqSheet indexSheet = store.getSheet(sheetName);
                if (indexSheet == null) break;
                for (CiqRow row : indexSheet.getRows()) {
                    String node    = row.get("Node");
                    String crGroup = row.get("CRGroup");   // normalize handles CR_GROUP too
                    if (node != null && !node.trim().isEmpty()) {
                        map.put(node.trim(), crGroup != null ? crGroup.trim() : "");
                    }
                }
                break;
            }
        }
        return map;
    }
}
