package com.rednetty.server.mechanics.economy;

/**
 * Represents the result of an economic transaction
 * Contains information about whether the transaction was successful
 * and any relevant messages or data.
 */
public class TransactionResult {

    private final boolean success;
    private final String message;
    private final int amount;

    /**
     * Creates a successful transaction result
     *
     * @param amount The amount that was involved in the transaction
     * @return A successful transaction result
     */
    public static TransactionResult success(int amount) {
        return new TransactionResult(true, "Transaction successful", amount);
    }

    /**
     * Creates a successful transaction result with a custom message
     *
     * @param message The success message
     * @param amount  The amount that was involved in the transaction
     * @return A successful transaction result
     */
    public static TransactionResult success(String message, int amount) {
        return new TransactionResult(true, message, amount);
    }

    /**
     * Creates a failed transaction result
     *
     * @param message The reason for failure
     * @return A failed transaction result
     */
    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, 0);
    }

    /**
     * Creates a transaction result
     *
     * @param success Whether the transaction was successful
     * @param message A message describing the result
     * @param amount  The amount involved in the transaction
     */
    private TransactionResult(boolean success, String message, int amount) {
        this.success = success;
        this.message = message;
        this.amount = amount;
    }

    /**
     * Checks if the transaction was successful
     *
     * @return true if the transaction was successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the message describing the transaction result
     *
     * @return The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the amount involved in the transaction
     *
     * @return The amount
     */
    public int getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "TransactionResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", amount=" + amount +
                '}';
    }
}