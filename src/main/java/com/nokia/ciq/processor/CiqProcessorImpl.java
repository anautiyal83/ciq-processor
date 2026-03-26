package com.nokia.ciq.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.List;

/**
 * Default implementation of {@link CiqProcessor}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link InMemoryExcelReader} reads the CIQ Excel workbook into an
 *       {@link InMemoryCiqDataStore} — no JSON files written.</li>
 *   <li>{@link ValidationRulesLoader} loads YAML validation rules.</li>
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
                                    List<ReportFormat> formats,
                                    String mopJsonOutputDir,
                                    String reportFileName) throws IOException {

        log.info("=== CIQ Processor ===");
        log.info("CIQ file:  {}", ciqFilePath);
        log.info("Node type: {}, Activity: {}", nodeType, activity);
        log.info("Rules:     {}", rulesFilePath);
        log.info("Formats:   {}", formats);

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        // Step 1: Read Excel into memory
        InMemoryCiqDataStore store =
                new InMemoryExcelReader().read(ciqFilePath, nodeType, activity);

        // Step 2: Load validation rules
        ValidationRulesConfig rules = new ValidationRulesLoader().load(rulesFilePath);

        // Step 3: Validate
        ValidationReport report =
                new CiqValidationEngine(store, rules).validate(nodeType, activity);

        // Step 4: Write reports
        String baseName = (reportFileName != null && !reportFileName.trim().isEmpty())
                ? reportFileName.trim()
                : nodeType + "_" + activity + "_validation-report";
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

        // Step 5: Child-order JSON segregation (only on PASSED)
        if ("PASSED".equals(report.getStatus()) && mopJsonOutputDir != null) {
            log.info("Segregating by child order into: {}", mopJsonOutputDir);
            segregateByChildOrder(store, nodeType, activity, mopJsonOutputDir);
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

        for (NodeEntry entry : index.getEntries()) {
            String node    = entry.getNode()    != null ? entry.getNode().trim()    : "";
            String crGroup = entry.getCrGroup() != null ? entry.getCrGroup().trim() : "";
            if (node.isEmpty()) continue;

            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;

            File childDir = new File(mopJsonOutputDir, childOrder);
            if (!childDir.exists()) childDir.mkdirs();

            // Filtered index (single entry for this child order)
            CiqIndex childIndex = new CiqIndex();
            childIndex.setNodeType(nodeType);
            childIndex.setActivity(activity);
            childIndex.getEntries().add(entry);
            childIndex.setNiamMapping(index.getNiamMapping());

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
}
