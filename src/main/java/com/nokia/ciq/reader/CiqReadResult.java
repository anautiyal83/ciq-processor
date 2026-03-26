package com.nokia.ciq.reader;

/**
 * Result of a {@link CiqReader#read} operation.
 *
 * <pre>
 * CiqReadResult result = reader.read(...);
 * if (result.getStatus() == CiqReadResult.Status.FAILED) {
 *     System.err.println("Error: " + result.getError());
 * } else {
 *     System.out.println(result.getMessage());
 * }
 * </pre>
 */
public class CiqReadResult {

    public enum Status { SUCCESS, FAILED }

    private final Status status;

    /** Human-readable error description; null on SUCCESS. */
    private final String error;

    /** Summary of what was processed (sheets read, JSON files created, etc.). */
    private final String message;

    private CiqReadResult(Status status, String error, String message) {
        this.status  = status;
        this.error   = error;
        this.message = message;
    }

    public static CiqReadResult success(String message) {
        return new CiqReadResult(Status.SUCCESS, null, message);
    }

    public static CiqReadResult failure(String error) {
        return new CiqReadResult(Status.FAILED, error, null);
    }

    public Status getStatus()  { return status; }
    public String getError()   { return error; }
    public String getMessage() { return message; }

    public boolean isSuccess() { return status == Status.SUCCESS; }

    @Override
    public String toString() {
        if (status == Status.FAILED) {
            return "FAILED: " + error;
        }
        return "SUCCESS: " + message;
    }
}
