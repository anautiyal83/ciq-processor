package com.nokia.ciq.validator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /**
     * Human-readable summary of every check that was applied to this sheet.
     * Populated regardless of pass/fail so callers can see exactly what was validated.
     * Examples:
     * <ul>
     *   <li>{@code "Column 'Action': required, enum[CREATE, MODIFY, DELETE]"}</li>
     *   <li>{@code "Column 'Node': required, crossRef"}</li>
     *   <li>{@code "Row rule: compare StartPort lessThanOrEquals EndPort"}</li>
     * </ul>
     */
    @JsonIgnore
    private List<String> checksApplied = new ArrayList<>();

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRowsChecked() { return rowsChecked; }
    public void setRowsChecked(int rowsChecked) { this.rowsChecked = rowsChecked; }

    public List<ValidationError> getErrors() { return errors; }
    public void setErrors(List<ValidationError> errors) { this.errors = errors; }

    public List<String> getChecksApplied() { return checksApplied; }
    public void setChecksApplied(List<String> checksApplied) { this.checksApplied = checksApplied; }

    public void addCheck(String check) { this.checksApplied.add(check); }

    public void addError(ValidationError error) {
        this.errors.add(error);
        this.status = "FAILED";
    }
}
