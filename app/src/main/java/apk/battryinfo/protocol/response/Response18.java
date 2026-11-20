package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response18 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("18", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
