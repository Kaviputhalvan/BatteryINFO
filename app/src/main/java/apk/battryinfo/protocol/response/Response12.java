package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response12 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("12", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
