package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class Response52 implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("52", payloadHex);

        // Decode only after the corresponding protocol fields are confirmed.
    }
}
