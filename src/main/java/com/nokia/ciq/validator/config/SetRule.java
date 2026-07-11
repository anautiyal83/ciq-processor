package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Partition-scoped subset rule.
 *
 * <p>For every partition on the {@code from} side, the set of collected values must
 * appear in the matching partition on the {@code to} side. The two sides can have
 * different partition columns — e.g. Index partitions by {@code (NodeGroup, CRGROUP)}
 * while the target table partitions by {@code NodeGroup} only. The target partition
 * key is derived from the source partition key by keeping only the columns listed in
 * {@code to.partitionBy}.
 *
 * <p>YAML example — every CONFIG_SEQ step listed in Index for a given
 * (NodeGroup, CRGROUP) must appear in IbcfTrunkGroupTable for the same NodeGroup:
 * <pre>
 * workbook_rules:
 *   - set:
 *       from:
 *         sheet: Index
 *         column: CONFIG_SEQ
 *         partitionBy: [NodeGroup, CRGROUP]
 *         where: "TABLES = IbcfTrunkGroupTable"
 *       to:
 *         sheet: IbcfTrunkGroupTable
 *         column: CONFIG_SEQ
 *         partitionBy: [NodeGroup]
 * </pre>
 */
public class SetRule {

    private Source from;
    private Target to;

    public Source getFrom() { return from; }
    public void setFrom(Source from) { this.from = from; }

    public Target getTo() { return to; }
    public void setTo(Target to) { this.to = to; }

    // -------------------------------------------------------------------------

    public static class Source {
        /** Sheet whose rows are grouped (e.g. "Index"). */
        private String sheet;
        /** Column whose distinct values are collected per partition. */
        private String column;
        /** Columns whose combined value defines each source partition. */
        private List<String> partitionBy;
        /**
         * Optional row filter applied before collecting values.
         * Format: {@code "ColumnName = value"} (single equality condition).
         */
        private String where;

        public String getSheet() { return sheet; }
        public void setSheet(String sheet) { this.sheet = sheet; }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public List<String> getPartitionBy() { return partitionBy; }
        public void setPartitionBy(List<String> partitionBy) { this.partitionBy = partitionBy; }

        public String getWhere() { return where; }
        public void setWhere(String where) { this.where = where; }
    }

    public static class Target {
        /** Sheet whose rows are grouped (e.g. "IbcfTrunkGroupTable"). */
        private String sheet;
        /** Column whose distinct values are collected per partition. */
        private String column;
        /**
         * Columns whose combined value defines each target partition. Must be a
         * subset of {@code from.partitionBy} (by column name).
         */
        private List<String> partitionBy;

        public String getSheet() { return sheet; }
        public void setSheet(String sheet) { this.sheet = sheet; }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public List<String> getPartitionBy() { return partitionBy; }
        public void setPartitionBy(List<String> partitionBy) { this.partitionBy = partitionBy; }
    }
}
