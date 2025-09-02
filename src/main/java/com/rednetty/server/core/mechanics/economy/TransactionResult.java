package com.rednetty.server.core.mechanics.economy;

/**
 * Represents the result of an economic transaction
 */
public class TransactionResult {
    private final boolean success;
    private final String message;
    private final int amount;
    private final long timestamp;

    private TransactionResult(boolean success, String message, int amount) {
        this.success = success;
        this.message = message != null ? message : "";
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    public static TransactionResult success(String message, int amount) {
        return new TransactionResult(true, message, amount);
    }

    public static TransactionResult success(int amount) {
        return new TransactionResult(true, "Transaction completed successfully", amount);
    }

    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, 0);
    }

    public static TransactionResult failure() {
        return new TransactionResult(false, "Transaction failed", 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public String getMessage() {
        return message;
    }

    public int getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

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