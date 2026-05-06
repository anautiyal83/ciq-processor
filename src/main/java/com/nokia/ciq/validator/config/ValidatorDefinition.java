package com.nokia.ciq.validator.config;

/**
 * Defines a custom {@link com.nokia.ciq.validator.validator.CellValidator} that can be
 * referenced from column rules via {@code validator: &lt;name&gt;}.
 *
 * <p>The YAML key {@code class} is mapped to the Java field {@code clazz} via a
 * SnakeYAML property alias because {@code class} is a reserved word in Java.
 *
 * <p>Example:
 * <pre>
 * validators:
 *   myValidator:
 *     class: com.example.MyCustomCellValidator
 *     description: "Validates some custom business rule"
 * </pre>
 */
public class ValidatorDefinition {

    /** Fully qualified class name of the {@link com.nokia.ciq.validator.validator.CellValidator}. */
    private String clazz;

    /** Optional human-readable description. */
    private String description;

    public String getClazz() { return clazz; }
    public void setClazz(String clazz) { this.clazz = clazz; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
