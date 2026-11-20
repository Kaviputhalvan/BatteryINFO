package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response7c implements ResponseHandler {

    private static final int REQUIRED_WORDS = 62;
    private static final int REQUIRED_HEX_LENGTH = REQUIRED_WORDS * 4;
    private static final int CELL_COUNT = BatteryData.PHYSICAL_CELL_COUNT;

    @Override
    public void handle(String payloadHex, BatteryData data) {
        if (data == null) return;
        data.saveRaw("7C", payloadHex);

        if (payloadHex == null ||
                payloadHex.length() < REQUIRED_HEX_LENGTH ||
                !payloadHex.substring(0, REQUIRED_HEX_LENGTH)
                        .matches("(?i)[0-9a-f]{248}")) {
            return;
        }

        try {
            int[] words = new int[REQUIRED_WORDS];

            for (int i = 0; i < REQUIRED_WORDS; i++) {
                words[i] = Integer.parseInt(
                        payloadHex.substring(i * 4, i * 4 + 4), 16);
            }

            for (int i = 0; i < CELL_COUNT; i++) {
                double voltage = words[i] * 0.001;

                if (!Double.isFinite(voltage) ||
                        voltage < 0.5 || voltage > 5.0) {
                    return;
                }
            }

            // Commit only after all 16 physical cell readings are valid.
            System.arraycopy(words, 0, data.rawWords, 0, REQUIRED_WORDS);
            System.arraycopy(words, 0, data.sevenCRawWords, 0, REQUIRED_WORDS);

            for (int i = 0; i < CELL_COUNT; i++) {
                data.cellVoltages[i] = words[i] * 0.001;
            }

            for (int i = CELL_COUNT; i < data.cellVoltages.length; i++) {
                data.cellVoltages[i] = 0;
            }

            for (int i = 0; i < 8; i++) {
                data.cellTemperatures[i] = words[32 + i] - 40;
            }

            for (int i = 0; i < 4; i++) {
                data.faultStates[i] = words[58 + i];
            }

            data.cellCount = CELL_COUNT;
            data.calculateCellSoc();

            double sum = 0;
            int count = 0;

            for (int i = 0; i < CELL_COUNT; i++) {
                if (data.cellSoc[i] >= 0 &&
                        data.cellSoc[i] <= 100) {
                    sum += data.cellSoc[i];
                    count++;
                }
            }

            data.soc = count > 0 ? sum / count : -1;
            data.liveDataAvailable = true;
            data.cellTelemetryAvailable = true;
            data.last7cReceivedAt = System.currentTimeMillis();

        } catch (RuntimeException e) {
            // Invalid packet: retain the last committed telemetry.
        }
    }
}
