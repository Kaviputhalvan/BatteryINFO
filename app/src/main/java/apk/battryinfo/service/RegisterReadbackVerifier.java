package apk.battryinfo.service;

import apk.battryinfo.model.BatteryData;

public final class RegisterReadbackVerifier {

    public enum State {
        IDLE, WAITING_FOR_READBACK, VERIFIED, MISMATCH, TIMEOUT, CANCELLED
    }

    private final BatteryData data;
    private State state = State.IDLE;
    private int address = -1;
    private int expectedValue;
    private int actualValue = -1;
    private long baselineSequence;
    private long deadlineMillis;

    public RegisterReadbackVerifier(BatteryData data) {
        if (data == null) throw new IllegalArgumentException("BatteryData required");
        this.data = data;
    }

    // Call only after the targeted read-back request has been sent.
    public synchronized void begin(int registerAddress, int expected, long timeoutMs) {
        if (registerAddress < 0 ||
                registerAddress >= BatteryData.REGISTER_CACHE_SIZE ||
                expected < 0 || expected > 0xFFFF || timeoutMs < 1) {
            throw new IllegalArgumentException("Invalid verification parameters");
        }

        address = registerAddress;
        expectedValue = expected;
        actualValue = -1;
        baselineSequence = data.getRegisterSequence(registerAddress);
        deadlineMillis = System.currentTimeMillis() + timeoutMs;
        state = State.WAITING_FOR_READBACK;
    }

    // Called after FC/E0 has been dispatched and its cache updated.
    public synchronized void onRegisterDataUpdated() {
        updateTimeout();
        if (state != State.WAITING_FOR_READBACK) return;

        long sequence = data.getRegisterSequence(address);
        if (sequence <= baselineSequence || !data.isRegisterValid(address)) return;

        actualValue = data.getRegisterValue(address);
        state = actualValue == expectedValue ? State.VERIFIED : State.MISMATCH;
    }

    private void updateTimeout() {
        if (state == State.WAITING_FOR_READBACK &&
                System.currentTimeMillis() >= deadlineMillis) {
            state = State.TIMEOUT;
        }
    }

    public synchronized State getState() {
        updateTimeout();
        return state;
    }

    public synchronized int getRegisterAddress() { return address; }
    public synchronized int getExpectedValue() { return expectedValue; }
    public synchronized int getActualValue() { return actualValue; }

    public synchronized void cancel() {
        if (state == State.WAITING_FOR_READBACK) state = State.CANCELLED;
    }

    public synchronized void reset() {
        state = State.IDLE;
        address = -1;
        expectedValue = 0;
        actualValue = -1;
        baselineSequence = 0;
        deadlineMillis = 0;
    }
}
