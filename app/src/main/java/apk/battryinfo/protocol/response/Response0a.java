package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response0a implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("0A", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
