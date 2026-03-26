package com.nokia.ciq.validator.model;

/**
 * A single validation error on a specific row and column.
 */
public class ValidationError {

    private int rowNumber;
    private String column;
    private String value;
    private String message;

    public ValidationError() {}

    public ValidationError(int rowNumber, String column, String value, String message) {
        this.rowNumber = rowNumber;
        this.column    = column;
        this.value     = value;
        this.message   = message;
    }

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
