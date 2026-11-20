package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response40 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("40", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
