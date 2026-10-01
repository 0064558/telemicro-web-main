package br.com.telemicro.api.budget;

public enum BudgetStatus {
    NEW, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canChangeTo(BudgetStatus target) {
        return switch (this) {
            case NEW -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> target == IN_PROGRESS;
        };
    }
    public boolean isClosed() { return this == COMPLETED || this == CANCELLED; }
}
