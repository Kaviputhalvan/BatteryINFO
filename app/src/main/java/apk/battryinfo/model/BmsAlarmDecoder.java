package apk.battryinfo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BmsAlarmDecoder {

    private BmsAlarmDecoder() {}

    private static final String[][] ALARMS = {
        {
            "Cell overvoltage - Level 1",
            "Cell overvoltage - Level 2",
            "Cell undervoltage - Level 1",
            "Cell undervoltage - Level 2",
            "Pack overvoltage - Level 1",
            "Pack overvoltage - Level 2",
            "Pack undervoltage - Level 1",
            "Pack undervoltage - Level 2",
            "Charging overtemperature - Level 1",
            "Charging overtemperature - Level 2",
            "Charging undertemperature - Level 1",
            "Charging undertemperature - Level 2",
            "Discharging overtemperature - Level 1",
            "Discharging overtemperature - Level 2",
            "Discharging undertemperature - Level 1",
            "Discharging undertemperature - Level 2"
        },
        {
            "Charging overcurrent - Level 1",
            "Charging overcurrent - Level 2",
            "Discharging overcurrent - Level 1",
            "Discharging overcurrent - Level 2",
            "SOC too high - Level 1",
            "SOC too high - Level 2",
            "SOC too low - Level 1",
            "SOC too low - Level 2",
            "Cell voltage difference too large - Level 1",
            "Cell voltage difference too large - Level 2",
            "Temperature difference too large - Level 1",
            "Temperature difference too large - Level 2",
            null, null, null, null
        },
        {
            "Charge MOS overtemperature",
            "Discharge MOS overtemperature",
            "Charge MOS sensor fault",
            "Discharge MOS sensor fault",
            "Charge MOS stuck",
            "Discharge MOS stuck",
            "Charge MOS short circuit",
            "Discharge MOS short circuit",
            "AFE chip fault",
            "CAN bus wire disconnection",
            "Single temperature sensor fault",
            "EEPROM fault",
            "RTC fault",
            "Precharge failure",
            "Vehicle communication fault",
            "Internal communication fault"
        },
        {
            "Current module fault",
            "Internal voltage module fault",
            "Short-circuit protection",
            "Low-voltage charging prohibition",
            "GPS software switch",
            "Charging relay/plug condition",
            "Thermal runaway",
            "Heating fault",
            "Balancing module communication fault",
            "Balancing conditions unmet",
            "Voltage sampling abnormal",
            "Battery full",
            null, null, null, null
        }
    };

    public static List<String> decode(int[] registers) {
        if (registers == null || registers.length < 4) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (int register = 0; register < 4; register++) {
            int value = registers[register] & 0xFFFF;

            for (int bit = 0; bit < 16; bit++) {
                String description = ALARMS[register][bit];

                if (description != null &&
                        (value & (1 << bit)) != 0) {
                    result.add("R" + (register + 1) +
                            ".B" + bit + " - " + description);
                }
            }
        }

        return result;
    }

    public static int count(int[] registers) {
        return decode(registers).size();
    }

    public static boolean hasAlarms(int[] registers) {
        return count(registers) > 0;
    }

    public static String[] getRegisterDefinitions(int register) {
        if (register < 0 || register >= 4) {
            return new String[0];
        }

        return ALARMS[register].clone();
    }
}
