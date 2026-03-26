package com.nokia.ciq.validator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation result for one CIQ sheet.
 */
public class SheetValidationResult {

    private String sheetName;
    private String status;        // PASSED | FAILED
    private int rowsChecked;
    private List<ValidationError> errors = new ArrayList<>();

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRowsChecked() { return rowsChecked; }
    public void setRowsChecked(int rowsChecked) { this.rowsChecked = rowsChecked; }

    public List<ValidationError> getErrors() { return errors; }
    public void setErrors(List<ValidationError> errors) { this.errors = errors; }

    public void addError(ValidationError error) {
        this.errors.add(error);
        this.status = "FAILED";
    }
}
