package source.treasury.application.port.out;

public interface CircuitBreakerPort {

    void haltDeposits();

    void haltWithdrawals();

    boolean isDepositsHalted();

    boolean isWithdrawalsHalted();

    void reset();
}
