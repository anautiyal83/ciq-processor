package com.nokia.ciq.validator.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads a {@link ValidationRulesConfig} from a YAML file.
 *
 * <p>Supports the full Schema.yaml framework: workbook_rules, validators, per-sheet
 * settings, row rules with conditions, etc.  Unknown YAML keys are silently ignored
 * so that schema files with extra fields do not cause failures.
 */
public class ValidationRulesLoader {

    public ValidationRulesConfig load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("Validation rules file not found: " + filePath);
        }

        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(ValidationRulesConfig.class, options);

        // ---- Skip unknown properties (e.g. version, settings keys from schema files) ----
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        // ---- ColumnRule: element type for allowedRanges list ----
        TypeDescription columnRuleDesc = new TypeDescription(ColumnRule.class);
        columnRuleDesc.addPropertyParameters("allowedRanges", IntRange.class);
        constructor.addTypeDescription(columnRuleDesc);

        // ---- SheetRules: element type for rules list ----
        TypeDescription sheetRulesDesc = new TypeDescription(SheetRules.class);
        sheetRulesDesc.addPropertyParameters("rules", SheetRowRule.class);
        constructor.addTypeDescription(sheetRulesDesc);

        // ---- RowCondition: element types for all/any lists ----
        TypeDescription rowConditionDesc = new TypeDescription(RowCondition.class);
        rowConditionDesc.addPropertyParameters("all", RowCondition.class);
        rowConditionDesc.addPropertyParameters("any", RowCondition.class);
        constructor.addTypeDescription(rowConditionDesc);

        // ---- ValidationRulesConfig:
        //      YAML key "workbook_rules" → Java property "workbookRules"
        //      YAML key "json_output"    → Java property "jsonOutput"
        //      (substituteProperty maps YAML name → getter/setter method names)
        TypeDescription rootDesc = new TypeDescription(ValidationRulesConfig.class);
        rootDesc.addPropertyParameters("workbookRules", WorkbookRule.class);
        rootDesc.substituteProperty("workbook_rules", List.class,
                "getWorkbookRules", "setWorkbookRules", WorkbookRule.class);
        rootDesc.substituteProperty("node_id_config", NodeIdConfig.class,
                "getNodeIdConfig", "setNodeIdConfig");
        rootDesc.substituteProperty("json_output", Map.class,
                "getJsonOutput", "setJsonOutput");
        rootDesc.substituteProperty("report_output", ReportOutputConfig.class,
                "getReportOutput", "setReportOutput");
        constructor.addTypeDescription(rootDesc);

        // ---- ValidatorDefinition: YAML key "class" → Java field "clazz"
        //      "class" is a Java reserved word so the field is named "clazz".
        TypeDescription validatorDefDesc = new TypeDescription(ValidatorDefinition.class);
        validatorDefDesc.substituteProperty("class", String.class,
                "getClazz", "setClazz");
        constructor.addTypeDescription(validatorDefDesc);

        Yaml yaml = new Yaml(constructor);
        try (InputStream is = new FileInputStream(file)) {
            ValidationRulesConfig config = yaml.load(is);
            return config != null ? config : new ValidationRulesConfig();
        }
    }
}
