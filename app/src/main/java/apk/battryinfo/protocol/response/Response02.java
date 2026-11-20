package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response02 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("02", payloadHex);

        try {
            String value = payloadHex.trim();

            if (value.isEmpty() || !value.matches("(?i)[0-9a-f]+")) {
                data.packCount = 0;
                return;
            }

            int count = Integer.parseInt(value, 16);
            data.packCount = count >= 0 && count <= 6 ? count : 0;

            data.packAddresses.clear();

            for (int i = 0; i < data.packCount; i++) {
                data.packAddresses.add(
                        String.format(java.util.Locale.US, "%02X", 0xD2 + i)
                );
            }

            if (data.packAddresses.isEmpty()) {
                data.packAddresses.add("D2");
            }

            data.activePackAddress = data.packAddresses.get(0);

        } catch (RuntimeException e) {
            data.packCount = 0;
            data.packAddresses.clear();
            data.packAddresses.add("D2");
            data.activePackAddress = "D2";
        }
    }
}
