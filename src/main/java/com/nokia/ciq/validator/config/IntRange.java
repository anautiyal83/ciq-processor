package com.nokia.ciq.validator.config;

/**
 * A closed integer range [min, max] used in {@code allowedRanges} rules.
 *
 * <pre>
 * allowedRanges:
 *   - min: 1
 *     max: 1024
 *   - min: 5001
 *     max: 7048
 *   - min: 0
 *     max: 0
 * </pre>
 *
 * A value passes if it falls within at least one of the listed ranges.
 */
public class IntRange {

    private long min;
    private long max;

    public long getMin() { return min; }
    public void setMin(long min) { this.min = min; }

    public long getMax() { return max; }
    public void setMax(long max) { this.max = max; }

    @Override
    public String toString() { return min == max ? String.valueOf(min) : min + ".." + max; }
}
