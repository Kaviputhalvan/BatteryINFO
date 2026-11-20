package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class ResponseFc implements ResponseHandler {

    private static final int REQUIRED_HEX_LENGTH = 248;
    private static final int REQUIRED_WORDS = 62;
    private static final int CELL_COUNT = BatteryData.PHYSICAL_CELL_COUNT;

    @Override
    public void handle(String payloadHex, BatteryData data) {
        if (data == null) return;
        data.saveRaw("FC", payloadHex);

        // FC starts at absolute register 0. Cache only received words.
        data.cacheRegisterBlock(0, payloadHex, 126);

        if (payloadHex == null ||
                payloadHex.length() < REQUIRED_HEX_LENGTH ||
                !payloadHex.substring(0, REQUIRED_HEX_LENGTH)
                        .matches("(?i)[0-9a-f]{248}")) {
            return;
        }

        try {
            int[] words = new int[REQUIRED_WORDS];

            for (int i = 0; i < REQUIRED_WORDS; i++) {
                int offset = i * 4;
                words[i] = Integer.parseInt(
                        payloadHex.substring(offset, offset + 4), 16);
            }

            // Validate the actual physical 16S pack before changing live data.
            double voltageSum = 0;
            double highestCell = 0;
            double lowestCell = Double.MAX_VALUE;

            for (int i = 0; i < CELL_COUNT; i++) {
                double voltage = words[i] * 0.001;

                if (!Double.isFinite(voltage) ||
                        voltage < 0.5 || voltage > 5.0) {
                    return;
                }

                voltageSum += voltage;
                highestCell = Math.max(highestCell, voltage);
                lowestCell = Math.min(lowestCell, voltage);
            }

            int enabled = 0;
            int balancingCycle = 0;
            int balancingCurrentRaw = 0;
            int balancingPosition = 0;
            int mosRaw = 0;
            boolean extendedAvailable = false;

            if (payloadHex.length() >= 268) {
                String extended = payloadHex.substring(248, 268);

                if (extended.matches("(?i)[0-9a-f]{20}")) {
                    balancingCycle = Integer.parseInt(
                            extended.substring(0, 4), 16);
                    enabled = Integer.parseInt(
                            extended.substring(4, 8), 16);
                    balancingCurrentRaw = Integer.parseInt(
                            extended.substring(8, 12), 16);
                    balancingPosition = Integer.parseInt(
                            extended.substring(12, 16), 16);
                    mosRaw = Integer.parseInt(
                            extended.substring(16, 20), 16);
                    extendedAvailable = true;
                }
            }

            // Commit packet buffers only after validation succeeds.
            System.arraycopy(words, 0, data.rawWords, 0, REQUIRED_WORDS);
            System.arraycopy(words, 0, data.fcRawWords, 0, REQUIRED_WORDS);

            for (int i = 0; i < CELL_COUNT; i++) {
                data.cellVoltages[i] = words[i] * 0.001;
            }

            data.cellCount = CELL_COUNT;

            for (int i = CELL_COUNT; i < data.cellVoltages.length; i++) {
                data.cellVoltages[i] = 0;
            }

            data.totalVoltage = words[40] * 0.1;
            data.current = (words[41] - 30000) * 0.1;

            // Keep the previous valid BMS SOC if this packet has an invalid value.
            double candidateSoc = words[42] / 10.0;
            if (Double.isFinite(candidateSoc) &&
                    candidateSoc >= 0 && candidateSoc <= 100) {
                data.reportedSoc = candidateSoc;
                data.reportedSocAvailable = true;
            }

            data.maximumCellVoltage = words[43] * 0.001;
            data.minimumCellVoltage = words[44] * 0.001;
            data.maximumTemperature = words[45] - 40;
            data.minimumTemperature = words[46] - 40;

            data.chargeDischargeStatus = words[47];
            data.remainingCapacity = words[48] * 0.1;
            data.temperatureSensorCount =
                    Math.max(0, Math.min(8, words[50]));

            data.cycles = words[51];
            data.balancingState = words[52];
            data.chargingMos = words[53];
            data.dischargeMos = words[54];
            data.averageVoltage = words[55] * 0.001;
            data.differentialPressure = words[56] * 0.001;
            data.power = words[57];

            for (int i = 0; i < 8; i++) {
                data.cellTemperatures[i] = words[32 + i] - 40;
            }

            for (int i = 0; i < 4; i++) {
                data.faultStates[i] = words[58 + i];
            }

            if (data.maximumCellVoltage <= 0) {
                data.maximumCellVoltage = highestCell;
            }
            if (data.minimumCellVoltage <= 0) {
                data.minimumCellVoltage = lowestCell;
            }
            if (data.averageVoltage <= 0) {
                data.averageVoltage = voltageSum / CELL_COUNT;
            }

            data.balancingAvailable = extendedAvailable;
            data.mosTemperatureAvailable = false;

            if (extendedAvailable) {
                data.balancingCycle = balancingCycle;
                data.balancingEnabled = enabled == 1;
                data.balancingCurrent =
                        (balancingCurrentRaw - 30000) * 0.1;
                data.balancingPosition = balancingPosition;
                data.mosTemperature = mosRaw - 40;
                data.mosTemperatureAvailable = data.mosTemperature > -40;
            }

            data.calculateCellSoc();

            double socSum = 0;
            int socCount = 0;

            for (int i = 0; i < CELL_COUNT; i++) {
                if (data.cellSoc[i] >= 0 &&
                        data.cellSoc[i] <= 100) {
                    socSum += data.cellSoc[i];
                    socCount++;
                }
            }

            data.soc = socCount > 0 ? socSum / socCount : -1;

            data.liveDataAvailable = true;
            data.cellTelemetryAvailable = true;
            data.fcTelemetryAvailable = true;
            data.lastFcReceivedAt = System.currentTimeMillis();

        } catch (RuntimeException e) {
            // Invalid packet: retain the last committed telemetry.
        }
    }
}
