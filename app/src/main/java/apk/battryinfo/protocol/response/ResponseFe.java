package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class ResponseFe implements ResponseHandler {

    @Override
    public void handle(String payloadHex, BatteryData data) {
        data.saveRaw("FE", payloadHex);
    }
}
