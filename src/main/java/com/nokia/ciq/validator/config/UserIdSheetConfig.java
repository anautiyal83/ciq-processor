package com.nokia.ciq.validator.config;

/**
 * Configuration for the USER_ID sheet — extends column validation rules
 * with the sheet name and the key column names used to map a CRGROUP to the
 * responsible user's email address.
 *
 * <p>YAML example:
 * <pre>
 * userIdSheet:
 *   name:          User_ID       # sheet name (default: User_ID)
 *   crGroupColumn: CRGROUP       # column that holds the CR group name (default: CRGROUP)
 *   emailColumn:   EMAIL         # column that holds the user email (default: EMAIL)
 *   columns:
 *     CRGROUP:
 *       required: true
 *     EMAIL:
 *       required: true
 * </pre>
 */
public class UserIdSheetConfig extends SheetRules {

    /** Sheet name. Defaults to {@code "User_ID"} when not specified in YAML. */
    private String name = "User_ID";

    /** Column that contains the CR group name. Defaults to {@code "CRGROUP"}. */
    private String crGroupColumn = "CRGROUP";

    /** Column that contains the responsible user's email. Defaults to {@code "EMAIL"}. */
    private String emailColumn = "EMAIL";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCrGroupColumn() { return crGroupColumn; }
    public void setCrGroupColumn(String crGroupColumn) { this.crGroupColumn = crGroupColumn; }

    public String getEmailColumn() { return emailColumn; }
    public void setEmailColumn(String emailColumn) { this.emailColumn = emailColumn; }
}
