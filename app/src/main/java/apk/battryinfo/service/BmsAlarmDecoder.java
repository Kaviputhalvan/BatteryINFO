package apk.battryinfo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BmsAlarmDecoder {

    public static final class Alarm {
        public final int register;
        public final int word;
        public final int bit;
        public final String title;
        public final String level;

        Alarm(int register, int bit, String title, String level) {
            this.register = register;
            this.word = 57 + register;
            this.bit = bit;
            this.title = title;
            this.level = level;
        }

        public String code() {
            return String.format(Locale.US, "R%d.B%d", register, bit);
        }
    }

    private static final String[][] TITLES = {
        {
            "Cell overvoltage",
            "Cell overvoltage",
            "Cell undervoltage",
            "Cell undervoltage",
            "Pack overvoltage",
            "Pack overvoltage",
            "Pack undervoltage",
            "Pack undervoltage",
            "Charging temperature overtemperature",
            "Charging temperature overtemperature",
            "Charging temperature undertemperature",
            "Charging temperature undertemperature",
            "Discharging temperature overtemperature",
            "Discharging temperature overtemperature",
            "Discharging temperature undertemperature",
            "Discharging temperature undertemperature"
        },
        {
            "Charge overcurrent",
            "Charge overcurrent",
            "Discharge overcurrent",
            "Discharge overcurrent",
            "SOC too high",
            "SOC too high",
            "SOC too low",
            "SOC too low",
            "Cell voltage difference too large",
            "Cell voltage difference too large",
            "Temperature difference too large",
            "Temperature difference too large"
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
            "CAN communication disconnected",
            "Temperature sensor fault",
            "EEPROM fault",
            "RTC fault",
            "Precharge failure",
            "Vehicle communication fault",
            "Internal communication fault"
        },
        {
            "Current module fault",
            "Internal total-voltage module fault",
            "Short-circuit protection",
            "Low-voltage charging prohibition",
            "GPS software switch",
            "Charging relay / plug condition",
            "Thermal runaway",
            "Heating fault",
            "Balancing module communication fault",
            "Balancing conditions not met",
            "Voltage sampling abnormal",
            "Battery full"
        }
    };

    private static final String[][] LEVELS = {
        {
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2"
        },
        {
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2",
            "Level 1", "Level 2"
        },
        {},
        {}
    };

    private BmsAlarmDecoder() {}

    public static List<Alarm> decode(int[] registers) {
        List<Alarm> result = new ArrayList<>();

        if (registers == null) return result;

        for (int r = 0; r < 4; r++) {
            if (r >= registers.length) break;

            int word = registers[r] & 0xFFFF;

            for (int bit = 0; bit < 16; bit++) {
                if ((word & (1 << bit)) == 0) continue;

                // Register 2 bits 12-15 are unmapped.
                // Register 4 bits 12-15 are reserved.
                if (bit >= TITLES[r].length) continue;

                String level = bit < LEVELS[r].length
                        ? LEVELS[r][bit] : "";

                result.add(new Alarm(
                        r + 1,
                        bit,
                        TITLES[r][bit],
                        level
                ));
            }
        }

        return result;
    }

    public static int[] fromWords(int[] rawWords) {
        int[] registers = new int[4];

        if (rawWords == null) return registers;

        for (int i = 0; i < 4; i++) {
            int wordIndex = 58 + i;
            if (wordIndex < rawWords.length) {
                registers[i] = rawWords[wordIndex] & 0xFFFF;
            }
        }

        return registers;
    }
}
