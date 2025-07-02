package com.rednetty.server.mechanics.economy;

/**
 * Represents the result of an economic transaction
 */
public class TransactionResult {
    private final boolean success;
    private final String message;
    private final int amount;
    private final long timestamp;

    /**
     * Private constructor for TransactionResult
     *
     * @param success Whether the transaction was successful
     * @param message The result message
     * @param amount  The amount involved in the transaction
     */
    private TransactionResult(boolean success, String message, int amount) {
        this.success = success;
        this.message = message != null ? message : "";
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Create a successful transaction result
     *
     * @param message The success message
     * @param amount  The amount involved
     * @return A successful TransactionResult
     */
    public static TransactionResult success(String message, int amount) {
        return new TransactionResult(true, message, amount);
    }

    /**
     * Create a successful transaction result with default message
     *
     * @param amount The amount involved
     * @return A successful TransactionResult
     */
    public static TransactionResult success(int amount) {
        return new TransactionResult(true, "Transaction completed successfully", amount);
    }

    /**
     * Create a failed transaction result
     *
     * @param message The failure message
     * @return A failed TransactionResult
     */
    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, 0);
    }

    /**
     * Create a failed transaction result with default message
     *
     * @return A failed TransactionResult
     */
    public static TransactionResult failure() {
        return new TransactionResult(false, "Transaction failed", 0);
    }

    /**
     * Check if the transaction was successful
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Check if the transaction failed
     *
     * @return true if failed, false otherwise
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Get the transaction result message
     *
     * @return The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the amount involved in the transaction
     *
     * @return The amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Get the timestamp of when this result was created
     *
     * @return The timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get a formatted string representation of this result
     *
     * @return Formatted string
     */
    public String getFormattedResult() {
        StringBuilder result = new StringBuilder();
        result.append(success ? "SUCCESS" : "FAILURE");
        result.append(": ").append(message);
        if (amount > 0) {
            result.append(" (Amount: ").append(amount).append(")");
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return "TransactionResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TransactionResult that = (TransactionResult) obj;
        return success == that.success &&
                amount == that.amount &&
                message.equals(that.message);
    }

    @Override
    public int hashCode() {
        int result = success ? 1 : 0;
        result = 31 * result + message.hashCode();
        result = 31 * result + amount;
        return result;
    }
}