package apk.battryinfo.service;

import apk.battryinfo.model.BatteryData;
import apk.battryinfo.protocol.ResponseDispatcher;

public class BatteryService {
    private final BatteryData data = new BatteryData();
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();
    private final RegisterReadbackVerifier verifier =
            new RegisterReadbackVerifier(data);

    public BatteryData getData() {
        return data;
    }

    public synchronized boolean receive(String code, String payload) {
        boolean handled = dispatcher.dispatch(code, payload, data);

        if (handled && ("FC".equalsIgnoreCase(code) ||
                "E0".equalsIgnoreCase(code))) {
            verifier.onRegisterDataUpdated();
        }

        return handled;
    }

    public RegisterReadbackVerifier getReadbackVerifier() {
        return verifier;
    }
}
