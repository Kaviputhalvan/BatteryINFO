package apk.battryinfo.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatteryData {
    public static final int PHYSICAL_CELL_COUNT = 16;
    public final Map<String, String> rawResponses = new HashMap<>();

    // Absolute BMS register cache. Uncovered registers remain invalid.
    public static final int REGISTER_CACHE_SIZE = 240;
    public final int[] registerValues = new int[REGISTER_CACHE_SIZE];
    public final boolean[] registerValid = new boolean[REGISTER_CACHE_SIZE];
    public final long[] registerUpdatedAt = new long[REGISTER_CACHE_SIZE];
    public final long[] registerSequence = new long[REGISTER_CACHE_SIZE];

    // Increments once for every accepted FC/E0 register block.
    private long registerUpdateSequence;

    public synchronized boolean cacheRegisterBlock(
            int startAddress, String payloadHex, int maximumWords) {

        if (payloadHex == null ||
                payloadHex.isEmpty() ||
                (payloadHex.length() & 3) != 0 ||
                !payloadHex.matches("(?i)[0-9a-f]+")) {
            return false;
        }

        int wordCount = payloadHex.length() / 4;

        if (startAddress < 0 ||
                maximumWords < 1 ||
                wordCount < 1 ||
                wordCount > maximumWords ||
                startAddress + wordCount > REGISTER_CACHE_SIZE) {
            return false;
        }

        int[] parsed = new int[wordCount];

        try {
            for (int i = 0; i < wordCount; i++) {
                int offset = i * 4;
                parsed[i] = Integer.parseInt(
                        payloadHex.substring(offset, offset + 4), 16);
            }
        } catch (RuntimeException e) {
            return false;
        }

        long now = System.currentTimeMillis();

        synchronized (this) {
            registerUpdateSequence++;
            for (int i = 0; i < wordCount; i++) {
                int address = startAddress + i;
                registerValues[address] = parsed[i];
                registerValid[address] = true;
                registerUpdatedAt[address] = now;
                registerSequence[address] = registerUpdateSequence;
            }
        }

        return true;
    }

    public synchronized boolean isRegisterValid(int address) {
        return address >= 0 &&
                address < REGISTER_CACHE_SIZE &&
                registerValid[address];
    }

    public synchronized int getRegisterValue(int address) {
        if (!isRegisterValid(address)) {
            throw new IllegalStateException(
                    "Register is not available: " + address);
        }
        return registerValues[address];
    }

    public synchronized long getRegisterUpdatedAt(int address) {
        if (address < 0 || address >= REGISTER_CACHE_SIZE) return 0;
        return registerUpdatedAt[address];
    }

    public synchronized long getRegisterUpdateSequence() {
        return registerUpdateSequence;
    }

    public synchronized long getRegisterSequence(int address) {
        if (address < 0 || address >= REGISTER_CACHE_SIZE) return 0;
        return registerSequence[address];
    }

    public synchronized void clearRegisterCache() {
        java.util.Arrays.fill(registerValues, 0);
        java.util.Arrays.fill(registerValid, false);
        java.util.Arrays.fill(registerUpdatedAt, 0L);
        java.util.Arrays.fill(registerSequence, 0L);
        registerUpdateSequence = 0;
    }

    // Keep packet data separate; rawWords remains the latest packet.
    public int[] rawWords = new int[62];
    public int[] fcRawWords = new int[62];
    public int[] sevenCRawWords = new int[62];
    public double[] cellVoltages = new double[32];
    public double[] cellSoc = new double[32];
    public int[] cellTemperatures = new int[8];
    public int[] faultStates = new int[4];

    public double totalVoltage;
    public double current;
    public double soc; // Voltage-estimated SOC
    public double reportedSoc;
    public double maximumCellVoltage;
    public double minimumCellVoltage;
    public double maximumTemperature;
    public double minimumTemperature;
    public double remainingCapacity;
    public double averageVoltage;
    public double differentialPressure;

    public int chargeDischargeStatus;
    // -1 = discharging, 0 = unknown/idle, 1 = charging
    public int flowDirection;
    public int cellCount;
    public int temperatureSensorCount;
    public int cycles;
    public int balancingState;

    // Extended FC telemetry
    public int balancingCycle;
    public boolean balancingEnabled;
    public double balancingCurrent;
    public int balancingPosition;
    public int mosTemperature;

    public boolean balancingAvailable;
    public boolean mosTemperatureAvailable;
    public int chargingMos;
    public int dischargeMos;
    public int power;

    public boolean liveDataAvailable;
    public boolean cellTelemetryAvailable;
    public boolean fcTelemetryAvailable;

    // Wall-clock timestamps in milliseconds.
    public long lastFcReceivedAt;
    public long last7cReceivedAt;
    public boolean reportedSocAvailable;

    // Runtime estimates and completed-session history statistics.
    public boolean runtimeEstimateAvailable;
    public int estimatedDischargeMinutes;
    public int estimatedChargeMinutes;
    public int completedDischargeSessions;
    public double inferredFullCapacity;
    public double averageLoadCurrent;
    public double averageDischargeMinutes;
    public double dischargeRuntimeTrendPercent;

    public int packCount;
    public String activePackAddress = "D2";
    public final List<String> packAddresses = new ArrayList<>();

    public void saveRaw(String code, String payload) {
        rawResponses.put(code, payload);
    }

    public void calculateCellSoc() {
        for (int i = 0; i < cellSoc.length; i++) {
            cellSoc[i] = i < PHYSICAL_CELL_COUNT
                    ? CellSocEstimator.estimate(cellVoltages[i])
                    : -1;
        }
    }
}
