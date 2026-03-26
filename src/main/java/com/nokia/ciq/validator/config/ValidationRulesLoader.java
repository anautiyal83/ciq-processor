package com.nokia.ciq.validator.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads a {@link ValidationRulesConfig} from a YAML file.
 */
public class ValidationRulesLoader {

    public ValidationRulesConfig load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("Validation rules file not found: " + filePath);
        }
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(ValidationRulesConfig.class, options);
        // Tell SnakeYAML the element type of the allowedRanges list
        org.yaml.snakeyaml.TypeDescription columnRuleDesc =
                new org.yaml.snakeyaml.TypeDescription(ColumnRule.class);
        columnRuleDesc.addPropertyParameters("allowedRanges", IntRange.class);
        constructor.addTypeDescription(columnRuleDesc);

        Yaml yaml = new Yaml(constructor);
        try (InputStream is = new FileInputStream(file)) {
            ValidationRulesConfig config = yaml.load(is);
            return config != null ? config : new ValidationRulesConfig();
        }
    }
}
