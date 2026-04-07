package com.nokia.ciq.validator.config;

/**
 * A workbook-level cross-sheet validation rule.
 *
 * <p>Appears under {@code workbook_rules:} in the YAML.  Each rule may contain
 * at most one of: {@code subset}, {@code superset}, {@code match}, {@code unique}.
 *
 * <p>Example:
 * <pre>
 * workbook_rules:
 *   - subset:
 *       from: "DataSheet.Node"
 *       to:   "Node_ID.Node"
 *   - unique:
 *       columns: [Node, Interface]
 * </pre>
 */
public class WorkbookRule {

    /** All values in {@code from} must appear in {@code to}. */
    private SubsetRule subset;

    /** All values in {@code to} must appear in {@code from}. */
    private SubsetRule superset;

    /** Bi-directional: values must be identical in both directions. */
    private SubsetRule match;

    /** Values (or composite keys) must be unique across the listed columns. */
    private UniqueRule unique;

    public SubsetRule getSubset() { return subset; }
    public void setSubset(SubsetRule subset) { this.subset = subset; }

    public SubsetRule getSuperset() { return superset; }
    public void setSuperset(SubsetRule superset) { this.superset = superset; }

    public SubsetRule getMatch() { return match; }
    public void setMatch(SubsetRule match) { this.match = match; }

    public UniqueRule getUnique() { return unique; }
    public void setUnique(UniqueRule unique) { this.unique = unique; }
}
