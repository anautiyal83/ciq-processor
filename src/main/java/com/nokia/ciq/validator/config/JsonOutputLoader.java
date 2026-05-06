package com.nokia.ciq.validator.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads a standalone {@code *_json-output.yaml} file as a raw map consumed by
 * {@link com.nokia.ciq.processor.JsonTemplateEvaluator}.
 *
 * <p>The file contains only the content that would appear under the
 * {@code json_output:} key in a validation-rules YAML — i.e. it starts with
 * {@code output_mode:}, {@code segregate_by:}, and {@code data:} at root level.
 * It does <em>not</em> include a {@code json_output:} wrapper key.
 *
 * <p>File naming convention: {@code {NODE_TYPE}_{ACTIVITY}_json-output.yaml}
 *
 * <p>Example file ({@code MRF_ANNOUNCEMENT_LOADING_json-output.yaml}):
 * <pre>
 * output_mode: single
 * data:
 *   nodeType: MRF
 *   activity: ANNOUNCEMENT_LOADING
 *   nodes:
 *     _each: "DISTINCT Index.Node AS $node"
 *     node:    $node
 *     crGroup: Index.CRGroup
 *     niamID:  "Node_Details.'NIAM NAME' WHERE Node_Details.Node_Name = $node"
 *     tableData:
 *       _each: "ANNOUNCEMENT_FILES WHERE GROUP = Index.GROUP"
 *       INPUT_FILE:           INPUT_FILE
 *       MRF_DESTINATION_PATH: MRF_DESTINATION_PATH
 * </pre>
 *
 * <p>Usage in validation-rules YAML (alternative to inline {@code json_output:}):
 * <pre>
 * json_output_file: "MRF_ANNOUNCEMENT_LOADING_json-output.yaml"
 * </pre>
 *
 * <p>When both {@code json_output_file} and inline {@code json_output} are present,
 * the inline block takes precedence.
 *
 * <p>This class is intentionally independent of ciq-processor internals so that
 * {@code mop-generator-utility} and {@code network-command-executor-utility} can
 * load the same file directly without pulling in validation dependencies.
 */
public class JsonOutputLoader {

    /**
     * Loads the json-output YAML file at the given path.
     *
     * @param filePath absolute or relative path to the {@code *_json-output.yaml} file
     * @return raw template map (same structure as {@code ValidationRulesConfig#getJsonOutput()})
     * @throws IOException if the file does not exist or cannot be parsed
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("JSON output config file not found: " + filePath);
        }

        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(file)) {
            Object raw = yaml.load(is);
            if (raw == null) {
                throw new IOException("JSON output config file is empty: " + filePath);
            }
            if (!(raw instanceof Map)) {
                throw new IOException("JSON output config file must be a YAML map at root level: " + filePath);
            }
            return (Map<String, Object>) raw;
        }
    }
}
