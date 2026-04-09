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
import java.util.Collections;
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
 *   <li>{@link ValidationRulesLoader} loads YAML validation rules.</li>
 *   <li>{@link InMemoryExcelReader} reads the CIQ Excel workbook into an
 *       {@link InMemoryCiqDataStore}.</li>
 *   <li>{@link CiqValidationEngine} validates the in-memory data.</li>
 *   <li>Reports are written in the requested formats (JSON, HTML, Excel).</li>
 *   <li>If validation passed and {@code mopJsonOutputDir} is provided, segregated
 *       JSON files are written for MOP generation.</li>
 * </ol>
 *
 * <p>When {@code json_output:} is configured in the rules YAML, each segregation unit
 * (CR / Group / Node) produces a <em>single</em> JSON file whose structure is defined
 * by the {@code fields} and {@code rows} declarations.  When {@code json_output:} is
 * absent, the default CiqSheet / index format is used.
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

        // Step 1: Load validation rules
        ValidationRulesConfig rules = new ValidationRulesLoader().load(rulesFilePath);

        // Step 2: Read Excel into memory
        InMemoryExcelReader reader = new InMemoryExcelReader();
        InMemoryCiqDataStore store = reader.read(ciqFilePath, nodeType, activity, rules);

        // Step 3: Validate
        ValidationReport report =
                new CiqValidationEngine(store, rules).validate(nodeType, activity);

        // Step 4: Write reports
        String baseName = nodeType.toUpperCase() + "_" + activity.toUpperCase() + "_VALIDATION_REPORT";
        for (ReportFormat fmt : formats) {
            String fileName = baseName + "." + fmt.extension();
            String path = new File(outDir, fileName).getAbsolutePath();
            report.getParameters().put("REPORT_FILENAME_" + fmt.name(), fileName);
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
            Map<String, Map<String, List<String>>> crGroupData = reader.getCrGroupToGroupNodes();
            Map<String, List<String>> groupToNodes = reader.getGroupToNodes();

            // Ensure output directory exists once — no subfolders are ever created
            File mopOutDir = new File(mopJsonOutputDir);
            if (!mopOutDir.exists()) mopOutDir.mkdirs();

            OutputMode outputMode = resolveOutputMode(rules);
            Map<String, Object> template = resolveTemplate(rules);

            if (outputMode == OutputMode.SINGLE) {
                log.info("output_mode: single — writing one JSON for entire workbook: {}", mopJsonOutputDir);
                segregateSingle(store, crGroupData, groupToNodes, nodeType, activity, mopJsonOutputDir, template);
            } else {
                // INDIVIDUAL — auto-detect grouping from workbook structure
                if (!crGroupData.isEmpty()) {
                    log.info("CRGROUP mode — segregating into CRGROUP folders: {}", mopJsonOutputDir);
                    segregateCRGroupBased(store, crGroupData, nodeType, activity, mopJsonOutputDir, template);
                } else if (!groupToNodes.isEmpty()) {
                    log.info("GROUP mode — segregating into group folders: {}", mopJsonOutputDir);
                    segregateGroupBased(store, groupToNodes, nodeType, activity, mopJsonOutputDir, template);
                } else {
                    log.info("NODE mode — segregating by child order into: {}", mopJsonOutputDir);
                    segregateByChildOrder(store, nodeType, activity, mopJsonOutputDir, template);
                }
            }
        }

        return report;
    }

    // -------------------------------------------------------------------------
    // Child-order (NODE) segregation
    // -------------------------------------------------------------------------

    private void segregateByChildOrder(InMemoryCiqDataStore store,
                                       String nodeType,
                                       String activity,
                                       String mopJsonOutputDir,
                                       Map<String, Object> template) throws IOException {

        CiqIndex index = store.getIndex();

        if (index.getEntries().isEmpty()) {
            segregateNoIndex(store, nodeType, activity, mopJsonOutputDir, template);
            return;
        }


        for (NodeEntry entry : index.getEntries()) {
            String node    = entry.getNode()    != null ? entry.getNode().trim()    : "";
            String crGroup = entry.getCrGroup() != null ? entry.getCrGroup().trim() : "";
            if (node.isEmpty()) continue;

            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;

            File childDir = new File(mopJsonOutputDir);
            String fileName = FileNamingUtil.indexFileName(nodeType, activity, childOrder);

            if (template != null && !template.isEmpty()) {
                // ── Single structured JSON via template ──
                Map<String, List<CiqRow>> filteredRows = buildNodeFilteredRows(store, entry, node);
                JsonTemplateEvaluator.TemplateContext ctx = new JsonTemplateEvaluator.TemplateContext(store, filteredRows);
                Object json = new JsonTemplateEvaluator().evaluate(template, ctx);
                mapper.writeValue(new File(childDir, fileName), json);
                log.info("  [{}] single JSON → {}", childOrder, fileName);

            } else {
                // ── Default: index file + per-table CiqSheet files ──
                Map<String, String> niamSubset = new LinkedHashMap<>();
                String niamId = index.getNiamMapping().get(node);
                if (niamId != null) niamSubset.put(node, niamId);

                CiqIndex childIndex = new CiqIndex();
                childIndex.setNodeType(nodeType);
                childIndex.setActivity(activity);
                childIndex.getEntries().add(entry);
                childIndex.setNiamMapping(niamSubset);

                mapper.writeValue(new File(childDir, fileName), childIndex);
                log.info("  [{}] index → {}", childOrder, fileName);

                for (String tableName : entry.getTables()) {
                    CiqSheet sheet = store.getSheet(tableName);
                    if (sheet == null) {
                        log.warn("  [{}] table '{}' not found — skipping", childOrder, tableName);
                        continue;
                    }
                    List<CiqRow> filteredRows = new ArrayList<>();
                    for (CiqRow row : sheet.getRows()) {
                        if (node.equals(row.get("Node"))) filteredRows.add(row);
                    }
                    CiqSheet filteredSheet = new CiqSheet();
                    filteredSheet.setSheetName(sheet.getSheetName());
                    filteredSheet.setColumns(sheet.getColumns());
                    filteredSheet.setRows(filteredRows);

                    String sheetFileName =
                            FileNamingUtil.sheetFileName(nodeType, activity, tableName, childOrder);
                    mapper.writeValue(new File(childDir, sheetFileName), filteredSheet);
                    log.info("  [{}] table '{}' ({} rows) → {}",
                            childOrder, tableName, filteredRows.size(), sheetFileName);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // CRGROUP-mode segregation
    // -------------------------------------------------------------------------

    private void segregateCRGroupBased(InMemoryCiqDataStore store,
                                       Map<String, Map<String, List<String>>> crGroupToGroupNodes,
                                       String nodeType,
                                       String activity,
                                       String mopJsonOutputDir,
                                       Map<String, Object> template) throws IOException {

        CiqIndex index = store.getIndex();

        List<String> dataSheets = indexDataSheets(index);

        for (Map.Entry<String, Map<String, List<String>>> crEntry : crGroupToGroupNodes.entrySet()) {
            String crGroup                         = crEntry.getKey();
            Map<String, List<String>> groupToNodes = crEntry.getValue();

            File crGroupDir = new File(mopJsonOutputDir);
            String fileName = FileNamingUtil.indexFileName(nodeType, activity, crGroup);

            if (template != null && !template.isEmpty()) {
                // ── Single structured JSON via template ──
                Map<String, List<CiqRow>> filteredRows =
                        buildCRGroupFilteredRows(store, crGroup, groupToNodes.keySet(), dataSheets);
                Map<String, String> nodeToGroup = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet())
                    for (String n : ge.getValue()) nodeToGroup.put(n, ge.getKey());
                JsonTemplateEvaluator.TemplateContext ctx = new JsonTemplateEvaluator.TemplateContext(store, filteredRows);
                Object json = new JsonTemplateEvaluator().evaluate(template, ctx);
                mapper.writeValue(new File(crGroupDir, fileName), json);
                log.info("CRGROUP {} → single JSON → {}", crGroup, fileName);

            } else {
                // ── Default: CRGroupIndex with embedded table data ──
                CRGroupIndex crGroupIndex = new CRGroupIndex();
                crGroupIndex.setNodeType(nodeType);
                crGroupIndex.setActivity(activity);
                crGroupIndex.setCrGroup(crGroup);

                List<CRGroupIndex.GroupEntry> groupEntries = new ArrayList<>();
                for (Map.Entry<String, List<String>> gEntry : groupToNodes.entrySet()) {
                    String group          = gEntry.getKey();
                    List<String> nodeList = gEntry.getValue();

                    Map<String, String> niamSubset = new LinkedHashMap<>();
                    for (String node : nodeList) {
                        String neid = index.getNiamMapping().get(node);
                        if (neid != null) niamSubset.put(node, neid);
                    }

                    Map<String, List<Map<String, String>>> tableData = new LinkedHashMap<>();
                    for (String tableName : dataSheets) {
                        CiqSheet sheet = store.getSheet(tableName);
                        if (sheet == null) {
                            log.warn("  [{}/{}] table '{}' not found — skipping",
                                    crGroup, group, tableName);
                            continue;
                        }
                        List<Map<String, String>> rows = new ArrayList<>();
                        for (CiqRow row : sheet.getRows()) {
                            if (group.equals(row.get("Group"))) rows.add(row.getData());
                        }
                        tableData.put(tableName, rows);
                        log.info("  [{}/{}] table '{}' — {} row(s) embedded",
                                crGroup, group, tableName, rows.size());
                    }

                    CRGroupIndex.GroupEntry ge = new CRGroupIndex.GroupEntry();
                    ge.setGroup(group);
                    ge.setNodes(nodeList);
                    ge.setNiamMapping(niamSubset);
                    ge.setTableData(tableData);
                    groupEntries.add(ge);
                }

                crGroupIndex.setGroups(groupEntries);
                mapper.writeValue(new File(crGroupDir, fileName), crGroupIndex);
                log.info("CRGROUP {} → groups {}, {} data sheet(s)",
                        crGroup, groupToNodes.keySet(), dataSheets.size());
            }
        }
    }

    // -------------------------------------------------------------------------
    // GROUP-mode segregation
    // -------------------------------------------------------------------------

    private void segregateGroupBased(InMemoryCiqDataStore store,
                                     Map<String, List<String>> groupToNodes,
                                     String nodeType,
                                     String activity,
                                     String mopJsonOutputDir,
                                     Map<String, Object> template) throws IOException {

        CiqIndex index = store.getIndex();

        List<String> dataSheets = indexDataSheets(index);

        for (Map.Entry<String, List<String>> entry : groupToNodes.entrySet()) {
            String group          = entry.getKey();
            List<String> nodeList = entry.getValue();

            File groupDir = new File(mopJsonOutputDir);
            String fileName = FileNamingUtil.indexFileName(nodeType, activity, group);

            if (template != null && !template.isEmpty()) {
                // ── Single structured JSON via template ──
                Map<String, List<CiqRow>> filteredRows =
                        buildGroupFilteredRows(store, group, dataSheets);
                Map<String, String> nodeToGroup = new LinkedHashMap<>();
                for (String n : nodeList) nodeToGroup.put(n, group);
                JsonTemplateEvaluator.TemplateContext ctx = new JsonTemplateEvaluator.TemplateContext(store, filteredRows);
                Object json = new JsonTemplateEvaluator().evaluate(template, ctx);
                mapper.writeValue(new File(groupDir, fileName), json);
                log.info("Group {} → single JSON → {}", group, fileName);

            } else {
                // ── Default: GroupIndex metadata + per-table CiqSheet files ──
                for (String tableName : dataSheets) {
                    CiqSheet sheet = store.getSheet(tableName);
                    if (sheet == null) {
                        log.warn("  [{}] table '{}' not found — skipping", group, tableName);
                        continue;
                    }
                    List<CiqRow> filteredRows = new ArrayList<>();
                    for (CiqRow row : sheet.getRows()) {
                        if (group.equals(row.get("Group"))) filteredRows.add(row);
                    }
                    CiqSheet filteredSheet = new CiqSheet();
                    filteredSheet.setSheetName(sheet.getSheetName());
                    filteredSheet.setColumns(sheet.getColumns());
                    filteredSheet.setRows(filteredRows);

                    String sheetFileName =
                            FileNamingUtil.sheetFileName(nodeType, activity, tableName, group);
                    mapper.writeValue(new File(groupDir, sheetFileName), filteredSheet);
                    log.info("  [{}] table '{}' ({} rows) → {}",
                            group, tableName, filteredRows.size(), sheetFileName);
                }

                Map<String, String> niamSubset = new LinkedHashMap<>();
                for (String node : nodeList) {
                    String neid = index.getNiamMapping().get(node);
                    if (neid != null) niamSubset.put(node, neid);
                }

                GroupIndex groupIndex = new GroupIndex();
                groupIndex.setNodeType(nodeType);
                groupIndex.setActivity(activity);
                groupIndex.setGroup(group);
                groupIndex.setNodes(nodeList);
                groupIndex.setTables(new ArrayList<>(dataSheets));
                groupIndex.setNiamMapping(niamSubset);

                mapper.writeValue(new File(groupDir, fileName), groupIndex);
                log.info("Group {} → nodes {}, {} data sheet(s)", group, nodeList, dataSheets.size());
            }
        }
    }

    // -------------------------------------------------------------------------
    // No-Index fallback segregation
    // -------------------------------------------------------------------------

    private void segregateNoIndex(InMemoryCiqDataStore store,
                                  String nodeType,
                                  String activity,
                                  String mopJsonOutputDir,
                                  Map<String, Object> template) throws IOException {

        List<String> dataSheets = new ArrayList<>();
        for (String name : store.getAvailableSheets()) {
            if (!name.replace("_", "").equalsIgnoreCase("index")) dataSheets.add(name);
        }

        Map<String, String> nodeToCrGroup = buildNodeCrGroupMap(store);
        boolean indexSheetPresent = !nodeToCrGroup.isEmpty();

        Set<String> nodes;
        if (indexSheetPresent) {
            nodes = new LinkedHashSet<>(nodeToCrGroup.keySet());
            log.info("INDEX sheet present — grouping {} node(s) by NODE + CRGroup: {}",
                    nodes.size(), nodes);
        } else {
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

            File childDir = new File(mopJsonOutputDir);
            String fileName = FileNamingUtil.indexFileName(nodeType, activity, childOrder);

            NodeEntry entry = new NodeEntry();
            entry.setNode(node);
            entry.setCrGroup(crGroup);
            entry.setTables(new ArrayList<>(dataSheets));

            if (template != null && !template.isEmpty()) {
                // ── Single structured JSON via template ──
                Map<String, List<CiqRow>> filteredRows =
                        buildNodeFilteredRows(store, entry, node);
                JsonTemplateEvaluator.TemplateContext ctx = new JsonTemplateEvaluator.TemplateContext(store, filteredRows);
                Object json = new JsonTemplateEvaluator().evaluate(template, ctx);
                mapper.writeValue(new File(childDir, fileName), json);
                log.info("  [{}] single JSON → {}", childOrder, fileName);

            } else {
                // ── Default: index file + per-table CiqSheet files ──
                CiqIndex childIndex = new CiqIndex();
                childIndex.setNodeType(nodeType);
                childIndex.setActivity(activity);
                childIndex.getEntries().add(entry);
                childIndex.setNiamMapping(store.getIndex().getNiamMapping());

                mapper.writeValue(new File(childDir, fileName), childIndex);
                log.info("  [{}] index → {}", childOrder, fileName);

                for (String tableName : dataSheets) {
                    CiqSheet sheet = store.getSheet(tableName);
                    if (sheet == null) continue;

                    List<CiqRow> filteredRows = new ArrayList<>();
                    for (CiqRow row : sheet.getRows()) {
                        if (node.equals(row.get("Node"))) filteredRows.add(row);
                    }
                    CiqSheet filtered = new CiqSheet();
                    filtered.setSheetName(sheet.getSheetName());
                    filtered.setColumns(sheet.getColumns());
                    filtered.setRows(filteredRows);

                    String sheetFileName =
                            FileNamingUtil.sheetFileName(nodeType, activity, tableName, childOrder);
                    mapper.writeValue(new File(childDir, sheetFileName), filtered);
                    log.info("  [{}] table '{}' ({} rows) → {}",
                            childOrder, tableName, filteredRows.size(), sheetFileName);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Filtered-row builders (per segregation mode)
    // -------------------------------------------------------------------------

    /**
     * Builds the filtered-row map for a NODE-mode segregation unit.
     * Data sheets are filtered by {@code Node == node}; the raw Index sheet is
     * filtered by the same node.
     */
    private Map<String, List<CiqRow>> buildNodeFilteredRows(InMemoryCiqDataStore store,
                                                             NodeEntry entry,
                                                             String node) throws IOException {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        for (String tableName : entry.getTables()) {
            CiqSheet sheet = store.getSheet(tableName);
            if (sheet == null) continue;
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : sheet.getRows()) {
                if (node.equals(row.get("Node"))) rows.add(row);
            }
            out.put(tableName, rows);
        }
        if (store.getRawIndexSheet() != null) {
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : store.getRawIndexSheet().getRows()) {
                if (node.equals(row.get("Node"))) rows.add(row);
            }
            out.put("Index", rows);
        }
        return out;
    }

    /**
     * Builds the filtered-row map for a GROUP-mode segregation unit.
     * Data sheets are filtered by {@code Group == group}; the raw Index sheet likewise.
     */
    private Map<String, List<CiqRow>> buildGroupFilteredRows(InMemoryCiqDataStore store,
                                                              String group,
                                                              List<String> dataSheets) throws IOException {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        for (String tableName : dataSheets) {
            CiqSheet sheet = store.getSheet(tableName);
            if (sheet == null) continue;
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : sheet.getRows()) {
                if (group.equals(row.get("Group"))) rows.add(row);
            }
            out.put(tableName, rows);
        }
        if (store.getRawIndexSheet() != null) {
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : store.getRawIndexSheet().getRows()) {
                if (group.equals(row.get("Group"))) rows.add(row);
            }
            out.put("Index", rows);
        }
        return out;
    }

    /**
     * Builds the filtered-row map for a CRGROUP-mode segregation unit.
     * Data sheets are filtered by rows whose GROUP column is in {@code groupsInCR};
     * the raw Index sheet is filtered by {@code CRGroup == crGroup}.
     */
    private Map<String, List<CiqRow>> buildCRGroupFilteredRows(InMemoryCiqDataStore store,
                                                                String crGroup,
                                                                Set<String> groupsInCR,
                                                                List<String> dataSheets) throws IOException {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        for (String tableName : dataSheets) {
            CiqSheet sheet = store.getSheet(tableName);
            if (sheet == null) continue;
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : sheet.getRows()) {
                String g = row.get("Group");
                if (g != null && groupsInCR.contains(g)) rows.add(row);
            }
            out.put(tableName, rows);
        }
        if (store.getRawIndexSheet() != null) {
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : store.getRawIndexSheet().getRows()) {
                if (crGroup.equals(row.get("CRGroup"))) rows.add(row);
            }
            out.put("Index", rows);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Single-output segregation (output_mode: single)
    // -------------------------------------------------------------------------

    private void segregateSingle(InMemoryCiqDataStore store,
                                 Map<String, Map<String, List<String>>> crGroupToGroupNodes,
                                 Map<String, List<String>> groupToNodes,
                                 String nodeType,
                                 String activity,
                                 String mopJsonOutputDir,
                                 Map<String, Object> template) throws IOException {

        if (template == null || template.isEmpty()) {
            log.warn("output_mode: single — no json_output template defined; skipping");
            return;
        }

        // Build whole-workbook nodeToGroup map (union of all CRs and groups)
        Map<String, String> nodeToGroup = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> crEntry : crGroupToGroupNodes.entrySet()) {
            for (Map.Entry<String, List<String>> ge : crEntry.getValue().entrySet()) {
                for (String n : ge.getValue()) nodeToGroup.put(n, ge.getKey());
            }
        }
        for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
            for (String n : ge.getValue()) nodeToGroup.putIfAbsent(n, ge.getKey());
        }

        Map<String, List<CiqRow>> allRows = buildAllRows(store);
        JsonTemplateEvaluator.TemplateContext ctx = new JsonTemplateEvaluator.TemplateContext(store, allRows);

        Object json = new JsonTemplateEvaluator().evaluate(template, ctx);

        String fileName = nodeType.toUpperCase() + "_" + activity.toUpperCase() + ".json";
        mapper.writeValue(new File(mopJsonOutputDir, fileName), json);
        log.info("SINGLE mode — wrote {}", fileName);
    }

    /**
     * Builds an unfiltered row map containing all rows for every data sheet,
     * every auxiliary sheet, and the full raw Index sheet.  Used in single-output mode
     * so the template engine can access all workbook data.
     */
    private Map<String, List<CiqRow>> buildAllRows(InMemoryCiqDataStore store) {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        // Data sheets (Index-referenced)
        for (String name : indexDataSheets(store.getIndex())) {
            CiqSheet s = store.getSheet(name);
            if (s != null) out.put(name, s.getRows());
        }
        // Auxiliary sheets (User_ID, Node_Details, etc.)
        for (String name : store.getAvailableSheets()) {
            if (!out.containsKey(name)) {
                CiqSheet s = store.getSheet(name);
                if (s != null) out.put(name, s.getRows());
            }
        }
        // Full unfiltered Index
        if (store.getRawIndexSheet() != null) {
            out.put("Index", store.getRawIndexSheet().getRows());
        }
        return out;
    }

    /**
     * Reads {@code output_mode} from inside the {@code json_output} block.
     * Defaults to {@link OutputMode#INDIVIDUAL} when absent.
     */
    @SuppressWarnings("unchecked")
    private OutputMode resolveOutputMode(ValidationRulesConfig rules) {
        Map<String, Object> jo = rules.getJsonOutput();
        log.info("[resolveOutputMode] jsonOutput map = {}", jo);
        if (jo == null) return OutputMode.INDIVIDUAL;
        Object mode = jo.get("output_mode");
        log.info("[resolveOutputMode] output_mode = {}", mode);
        return OutputMode.fromString(mode != null ? mode.toString() : null);
    }

    /**
     * Reads the {@code data} sub-map from inside the {@code json_output} block.
     * Returns {@code null} when the block or key is absent.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTemplate(ValidationRulesConfig rules) {
        Map<String, Object> jo = rules.getJsonOutput();
        if (jo == null) return null;
        Object data = jo.get("data");
        return (data instanceof Map) ? (Map<String, Object>) data : null;
    }

    /**
     * Returns the unique data-table names referenced in the Index entries, preserving
     * encounter order.  Auxiliary sheets (e.g. Node_Details, USER_ID) are excluded so
     * they are not filtered by group/node in filteredRows and can be looked up in full
     * via {@code store.getSheet()} by the template engine.
     */
    private List<String> indexDataSheets(CiqIndex index) {
        Set<String> seen = new LinkedHashSet<>();
        for (NodeEntry entry : index.getEntries()) {
            if (entry.getTables() != null) seen.addAll(entry.getTables());
        }
        return new ArrayList<>(seen);
    }

    private Map<String, String> buildNodeCrGroupMap(InMemoryCiqDataStore store) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String sheetName : store.getAvailableSheets()) {
            if (sheetName.replace("_", "").equalsIgnoreCase("index")) {
                CiqSheet indexSheet = store.getSheet(sheetName);
                if (indexSheet == null) break;
                for (CiqRow row : indexSheet.getRows()) {
                    String node    = row.get("Node");
                    String crGroup = row.get("CRGroup");
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
