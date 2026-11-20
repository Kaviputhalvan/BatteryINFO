package apk.battryinfo.model;

public final class CellSocEstimator {

    private CellSocEstimator() {}

    // Approximate NMC/NCA open-circuit voltage curve.
    // Values are estimates, not direct BMS measurements.
    private static final double[] VOLTAGE = {
            2.80, 3.00, 3.20, 3.30, 3.40,
            3.50, 3.60, 3.65, 3.70, 3.75,
            3.80, 3.85, 3.90, 3.95, 4.00,
            4.05, 4.10, 4.15, 4.20
    };

    private static final double[] SOC = {
             0,   1,   2,   5,   8,
            12,  20,  27,  35,  45,
            55,  65,  75,  83,  90,
            94,  97,  99, 100
    };

    public static double estimate(double voltage) {
        if (voltage < 2.80 || voltage > 4.25) {
            return -1;
        }

        if (voltage <= VOLTAGE[0]) return 0;
        if (voltage >= VOLTAGE[VOLTAGE.length - 1]) return 100;

        for (int i = 0; i < VOLTAGE.length - 1; i++) {
            if (voltage <= VOLTAGE[i + 1]) {
                double ratio = (voltage - VOLTAGE[i]) /
                        (VOLTAGE[i + 1] - VOLTAGE[i]);

                return SOC[i] +
                        ratio * (SOC[i + 1] - SOC[i]);
            }
        }

        return -1;
    }
}
