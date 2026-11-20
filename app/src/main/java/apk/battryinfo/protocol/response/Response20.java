package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response20 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("20", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
