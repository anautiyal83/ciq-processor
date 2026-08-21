package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Contiguous-sequence rule.
 *
 * <p>Within each partition (defined by {@code partitionBy}), every distinct value of
 * {@code column} must occupy a single <em>contiguous</em> run of rows.  A value that
 * reappears after a different value has intervened is an error — i.e. the sequence
 * values may not interleave or repeat non-contiguously.
 *
 * <p>This is order-sensitive and evaluated in sheet row order; it does NOT enforce a
 * particular ascending order between distinct values, only that each value's rows are
 * grouped together.
 *
 * <p>Fully generic — sheet and column names are supplied in the YAML.
 *
 * <p>YAML example — within each (NodeGroup, CRGROUP) in the Index, a CONFIG_SEQ step
 * must not restart once a later step has begun:
 * <pre>
 * workbook_rules:
 *   - contiguous_sequence:
 *       sheet: Index
 *       partitionBy: [NodeGroup, CRGROUP]
 *       column: CONFIG_SEQ
 * </pre>
 *
 * <p>Valid:   Step1, Step1, Step2, Step2, Step3, Step4
 * <p>Invalid: Step1, Step1, Step2, Step2, <b>Step1</b>, Step3   (Step1 returns after Step2)
 */
public class ContiguousSequenceRule {

    /** Sheet to inspect (rows are evaluated in their natural order). */
    private String sheet;

    /** Columns whose combined value defines each partition (e.g. [NodeGroup, CRGROUP]). */
    private List<String> partitionBy;

    /** Column whose values must form contiguous blocks within each partition. */
    private String column;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public List<String> getPartitionBy() { return partitionBy; }
    public void setPartitionBy(List<String> partitionBy) { this.partitionBy = partitionBy; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }
}
