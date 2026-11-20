package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public interface ResponseHandler {
    void handle(String payloadHex, BatteryData data);
}
