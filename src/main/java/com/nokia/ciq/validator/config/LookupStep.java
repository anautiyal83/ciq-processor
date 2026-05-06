package com.nokia.ciq.validator.config;

/**
 * One hop in a {@link ConditionalPattern#getLookupChain() lookupChain}.
 *
 * <p>Each step takes a set of incoming values, scans the configured sheet for rows
 * where {@code joinOn} matches an incoming value, and collects the corresponding
 * {@code extract} column values as the output passed to the next step (or used as
 * the final resolved values for {@code when} matching).
 *
 * <p>YAML example (two-step chain):
 * <pre>
 * lookupChain:
 *   - sheet: Index
 *     joinOn: CRGroup     # match incoming value against Index.CRGroup
 *     extract: Node       # carry Index.Node forward
 *   - sheet: Node_Details
 *     joinOn: Node_Name   # match carried Node value against Node_Details.Node_Name
 *     extract: VER        # final resolved value used in 'when' matching
 * </pre>
 */
public class LookupStep {

    /** Sheet to scan for matching rows. */
    private String sheet;

    /** Column in {@code sheet} to match the incoming value against (case-insensitive). */
    private String joinOn;

    /** Column in {@code sheet} to extract as the output of this step. */
    private String extract;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getJoinOn() { return joinOn; }
    public void setJoinOn(String joinOn) { this.joinOn = joinOn; }

    public String getExtract() { return extract; }
    public void setExtract(String extract) { this.extract = extract; }
}
