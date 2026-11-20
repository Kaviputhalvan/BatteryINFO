package apk.battryinfo.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BmsProtocol {

    private BmsProtocol() {}

    public static final String DEFAULT_ADDRESS = "D2";
    public static final String READ_FUNCTION = "03";
    public static final String WRITE_FUNCTION = "06";
    public static final String WRITE_MULTIPLE_FUNCTION = "10";

    public static final class ReadRequest {
        public final int start;
        public final int count;
        public final String name;

        public ReadRequest(int start, int count, String name) {
            this.start = start;
            this.count = count;
            this.name = name;
        }

        public int getEndAddress() {
            return start + count - 1;
        }

        public String getFrame() {
            return buildReadRequest(start, count);
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.US,
                    "%s: 0x%04X-0x%04X (%d registers) -> %s",
                    name, start, getEndAddress(), count, getFrame()
            );
        }
    }

    // D203 + start address + register count + CRC
    public static String buildReadRequest(int start, int count) {
        return buildReadRequest(DEFAULT_ADDRESS, start, count);
    }

    public static String buildReadRequest(
            String deviceAddress,
            int start,
            int count
    ) {
        if (deviceAddress == null ||
                !deviceAddress.matches("(?i)[0-9a-f]{2}")) {
            throw new IllegalArgumentException("Invalid device address");
        }

        if (start < 0 || start > 0xFFFF) {
            throw new IllegalArgumentException("Invalid start address");
        }

        if (count < 1 || count > 0xFFFF ||
                start + count - 1 > 0xFFFF) {
            throw new IllegalArgumentException("Invalid register count");
        }

        String body = deviceAddress.toUpperCase(Locale.ROOT)
                + READ_FUNCTION
                + String.format(Locale.US, "%04X%04X", start, count);

        return body + crc16(body);
    }

    // Confirmed PURE BMS CRC implementation:
    // CRC-16/MODBUS, init FFFF, polynomial A001, final byte swap.
    public static String crc16(String hexData) {
        if (hexData == null || (hexData.length() & 1) != 0 ||
                !hexData.matches("(?i)[0-9a-f]*")) {
            throw new IllegalArgumentException("Invalid CRC input");
        }

        int crc = 0xFFFF;

        for (int i = 0; i < hexData.length(); i += 2) {
            int value = Integer.parseInt(
                    hexData.substring(i, i + 2), 16
            );

            crc ^= value;

            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }

        int swapped = ((crc & 0xFF00) >>> 8)
                | ((crc & 0x00FF) << 8);

        return String.format(Locale.US, "%04X", swapped);
    }

    // Main-screen startup requests confirmed from PURE BMS.
    // Live data (0x0000, 0x007E) is triggered by an E0 response.
    public static List<ReadRequest> getReadPlan() {
        List<ReadRequest> requests = new ArrayList<>();

        requests.add(new ReadRequest(
                67, 1, "Battery pack count"
        ));

        requests.add(new ReadRequest(
                128, 41, "Main bike battery data"
        ));

        return Collections.unmodifiableList(requests);
    }

    public static List<String> getReadFrames() {
        List<String> frames = new ArrayList<>();

        for (ReadRequest request : getReadPlan()) {
            frames.add(request.getFrame());
        }

        return Collections.unmodifiableList(frames);
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0 ||
                !hex.matches("(?i)[0-9a-f]*")) {
            throw new IllegalArgumentException("Invalid hex string");
        }

        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(
                    hex.substring(i * 2, i * 2 + 2), 16
            );
        }

        return result;
    }
}
