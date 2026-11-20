package apk.battryinfo.protocol;

import java.util.HashMap;
import java.util.Map;

import apk.battryinfo.model.BatteryData;
import apk.battryinfo.protocol.response.*;

public class ResponseDispatcher {

    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    public ResponseDispatcher() {
        handlers.put("7C", new Response7c());
        handlers.put("20", new Response20());
        handlers.put("52", new Response52());
        handlers.put("40", new Response40());
        handlers.put("06", new Response06());
        handlers.put("12", new Response12());
        handlers.put("0A", new Response0a());
        handlers.put("FC", new ResponseFc());
        handlers.put("E0", new ResponseE0());
        handlers.put("FE", new ResponseFe());
        handlers.put("18", new Response18());
        handlers.put("38", new Response38());
        handlers.put("22", new Response22());
        handlers.put("02", new Response02());
    }

    public boolean dispatch(String responseCode, String payloadHex,
                            BatteryData data) {
        if (responseCode == null || payloadHex == null || data == null) {
            return false;
        }

        ResponseHandler handler =
                handlers.get(responseCode.toUpperCase());

        if (handler == null) return false;

        handler.handle(payloadHex, data);
        return true;
    }
}
