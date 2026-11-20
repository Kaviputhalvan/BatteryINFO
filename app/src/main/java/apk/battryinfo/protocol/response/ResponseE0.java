package apk.battryinfo.protocol.response;

import apk.battryinfo.model.BatteryData;

public class ResponseE0 implements ResponseHandler {

    private static final int START_REGISTER = 128;
    private static final int MAXIMUM_WORDS = 112;

    @Override
    public void handle(String payloadHex, BatteryData data) {
        if (data == null) return;

        data.saveRaw("E0", payloadHex);

        // E0 covers absolute registers 128 through 239.
        // The cache validates the payload and records its actual word count.
        data.cacheRegisterBlock(
                START_REGISTER, payloadHex, MAXIMUM_WORDS);
    }
}
