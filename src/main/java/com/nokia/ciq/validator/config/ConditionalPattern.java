package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Configuration for cross-sheet conditional pattern validation with multi-hop join support.
 *
 * <p>Starting from a column in the current row ({@code startColumn}), the validator
 * follows a chain of sheet joins ({@code lookupChain}) to resolve a final set of values,
 * then finds the first {@link ConditionalPatternEntry} whose {@code when} regex matches
 * any resolved value and checks the cell against the corresponding {@code pattern}.
 *
 * <p>This is fully generic — any relational path through the workbook sheets can be expressed.
 *
 * <p>YAML structure:
 * <pre>
 * INPUT_FILE:
 *   conditionalPattern:
 *     startColumn: GROUP          # column in current row to begin the join
 *     lookupChain:
 *       - sheet: Index
 *         joinOn: CRGroup         # match startColumn value against Index.CRGroup
 *         extract: Node           # carry Index.Node forward to next step
 *       - sheet: Node_Details
 *         joinOn: Node_Name       # match carried Node value against Node_Details.Node_Name
 *         extract: VER            # final resolved value used in 'when' matching
 *     rules:
 *       - when: "^13\\."
 *         pattern: "(?i).*\\.jar$"
 *         message: "Version 13.x requires a .jar file"
 *       - when: ".*"
 *         pattern: "(?i)^(?!.*\\.(jar|tar\\.gz|tgz|gz|zip|bz2|7z|rar)$).*$"
 *         message: "Plain non-compressed file required"
 * </pre>
 *
 * <p>Resolution steps for a single row:
 * <ol>
 *   <li>Read {@code startColumn} from the current row → initial value set (e.g. {@code {CR1}})</li>
 *   <li>Step 1: scan {@code Index} where {@code CRGroup} ∈ initial set → collect {@code Node} values</li>
 *   <li>Step 2: scan {@code Node_Details} where {@code Node_Name} ∈ node set → collect {@code VER} values</li>
 *   <li>For each resolved VER: find the first matching {@code when} rule and validate the cell value</li>
 * </ol>
 */
public class ConditionalPattern {

    /**
     * Column in the currently validated row whose value seeds the join chain.
     * Example: {@code GROUP} in the {@code ANNOUNCEMENT_FILES} sheet.
     */
    private String startColumn;

    /**
     * Ordered list of join steps that resolve the final lookup values from {@code startColumn}.
     * Each step takes the output of the previous step as its input.
     */
    private List<LookupStep> lookupChain;

    /**
     * Ordered list of conditional rules evaluated against the resolved lookup values.
     * First {@code when} match wins. A null/empty {@code when} acts as catch-all (place last).
     */
    private List<ConditionalPatternEntry> rules;

    public String getStartColumn() { return startColumn; }
    public void setStartColumn(String startColumn) { this.startColumn = startColumn; }

    public List<LookupStep> getLookupChain() { return lookupChain; }
    public void setLookupChain(List<LookupStep> lookupChain) { this.lookupChain = lookupChain; }

    public List<ConditionalPatternEntry> getRules() { return rules; }
    public void setRules(List<ConditionalPatternEntry> rules) { this.rules = rules; }
}
