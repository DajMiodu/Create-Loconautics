package com.lycoris.loconautics.trainphysics.drive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AxleGearing {

    public static final int MAX_GEAR = 15;
    public static final double FULL_RPM = 256.0;
    static final double HEADROOM_FILL = 0.95;

    public record Load(Long network, double capacity, double networkStress, double ownStress, double impact, int gear) {}

    public record Gearing(double ratio, double maxRatio) {
        public static final Gearing DIRECT = new Gearing(1.0, 1.0);
    }

    private AxleGearing() {
    }

    public static double stressImpact(double base, double massShare, double massDivisor,
                                      double slopeStress, double opposedFactor) {
        double impact = (base + massShare / Math.max(1.0e-9, massDivisor) + slopeStress) * opposedFactor;
        return Double.isFinite(impact) ? Math.max(0.0, impact) : 0.0;
    }

    public static double maxRatio(double capacity, double otherStress, double fullSpeedCost,
                                  double scale, double ceilingRatio) {
        double ceiling = Double.isFinite(ceilingRatio) ? Math.max(1.0, ceilingRatio) : 1.0;
        double free = capacity - Math.max(0.0, otherStress);
        if (!(free > 0.0) || !(scale > 0.0)) {
            return 1.0;
        }
        if (!(fullSpeedCost > 1.0e-9)) {
            return ceiling;
        }
        double ratio = scale * HEADROOM_FILL * free / fullSpeedCost;
        return Double.isFinite(ratio) ? Math.max(1.0, Math.min(ceiling, ratio)) : ceiling;
    }

    public static double gearRatio(int gear, double maxRatio) {
        if (gear <= 0 || !(maxRatio > 1.0)) {
            return 1.0;
        }
        return Math.pow(maxRatio, Math.min(gear, MAX_GEAR) / (double) MAX_GEAR);
    }

    public static Gearing[] solve(List<Load> loads, double scale, double ceilingRatio) {
        Map<Long, double[]> byNetwork = new HashMap<>();
        for (Load load : loads) {
            if (load.network() == null) {
                continue;
            }
            double[] sums = byNetwork.computeIfAbsent(load.network(), n -> new double[2]);
            sums[0] += load.impact() * FULL_RPM;
            sums[1] += Math.max(0.0, load.ownStress());
        }
        Gearing[] out = new Gearing[loads.size()];
        for (int i = 0; i < loads.size(); i++) {
            Load load = loads.get(i);
            if (load.network() == null) {
                out[i] = Gearing.DIRECT;
                continue;
            }
            double[] sums = byNetwork.get(load.network());
            double max = maxRatio(load.capacity(), load.networkStress() - sums[1], sums[0], scale, ceilingRatio);
            out[i] = new Gearing(gearRatio(load.gear(), max), max);
        }
        return out;
    }

    public static double gearedSpeed(double baselineAtFullRpm, double ratio, double rpm) {
        return baselineAtFullRpm * ratio * rpm / FULL_RPM;
    }

    public static double ceilingRatio(double baseline, double ceiling) {
        if (!(baseline > 1.0e-9) || !Double.isFinite(ceiling)) {
            return 1.0;
        }
        return Math.max(1.0, ceiling / baseline);
    }

    public static double speedCeiling(double configured, double safety, boolean capAtCreate, double createTop) {
        double ceiling = configured < 0.0 ? safety : configured;
        return capAtCreate ? Math.min(ceiling, createTop) : ceiling;
    }
}
