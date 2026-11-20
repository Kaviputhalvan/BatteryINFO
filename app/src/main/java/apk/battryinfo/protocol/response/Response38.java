package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response38 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("38", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
