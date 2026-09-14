package com.crispywafer.crispywaferguntracker;

/**
 * Pure TACZ-style ballistic math. TACZ moves a projectile first, then applies drag,
 * then applies gravity once per game tick. Fractional ticks represent movement along
 * the current tick's straight collision segment.
 */
public final class BallisticsMath {
    private static final double EPSILON = 1.0e-9;
    private static final double SCAN_STEP_TICKS = 0.25D;
    private static final int BISECTION_STEPS = 14;

    private BallisticsMath() {}

    public record Solution(
            boolean valid,
            double timeTicks,
            double aimX,
            double aimY,
            double aimZ,
            double predictedX,
            double predictedY,
            double predictedZ,
            double residual
    ) {
        public static Solution invalid() {
            return new Solution(false, 0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D, Double.POSITIVE_INFINITY);
        }
    }

    /** Distance travelled by the launch-direction component after t ticks. */
    public static double directionalTravel(double initialSpeed, double friction, double timeTicks) {
        if (initialSpeed <= EPSILON || timeTicks <= 0.0D) return 0.0D;
        return initialSpeed * dragTravelFactor(friction, timeTicks);
    }

    /**
     * Travel multiplier for any component present in the initial velocity, including
     * inherited shooter velocity from Projectile#shootFromRotation.
     */
    public static double dragTravelFactor(double friction, double timeTicks) {
        if (timeTicks <= 0.0D) return 0.0D;
        double r = retention(friction);
        int wholeTicks = (int) Math.floor(timeTicks);
        double fractional = timeTicks - wholeTicks;

        double sum;
        if (Math.abs(1.0D - r) <= EPSILON) {
            sum = wholeTicks;
        } else {
            sum = (1.0D - Math.pow(r, wholeTicks)) / (1.0D - r);
        }
        if (fractional > 0.0D) {
            sum += fractional * Math.pow(r, wholeTicks);
        }
        return sum;
    }

    /** Vertical displacement caused only by TACZ gravity, excluding launch Y velocity. */
    public static double gravityDisplacementY(double gravity, double friction, double timeTicks) {
        if (gravity <= 0.0D || timeTicks <= 0.0D) return 0.0D;
        double r = retention(friction);
        double f = 1.0D - r;
        int wholeTicks = (int) Math.floor(timeTicks);
        double fractional = timeTicks - wholeTicks;

        double y;
        double vyAtWholeTick;
        if (Math.abs(f) <= EPSILON) {
            // No drag: v_k = -g*k and sum(k=0..n-1) = n(n-1)/2.
            y = -gravity * wholeTicks * (wholeTicks - 1.0D) * 0.5D;
            vyAtWholeTick = -gravity * wholeTicks;
        } else {
            double geometric = (1.0D - Math.pow(r, wholeTicks)) / f;
            y = -gravity / f * (wholeTicks - geometric);
            vyAtWholeTick = -gravity * geometric;
        }
        return y + fractional * vyAtWholeTick;
    }

    /**
     * Solves a moving-target intercept using the same drag/gravity update order as TACZ.
     * All target coordinates are relative to the muzzle/eye origin. Velocity and
     * acceleration are blocks/tick and blocks/tick^2. inherited* is the shooter's
     * velocity contribution inherited by the projectile at launch.
     */
    public static Solution solveIntercept(
            double targetX, double targetY, double targetZ,
            double targetVx, double targetVy, double targetVz,
            double targetAx, double targetAy, double targetAz,
            double inheritedVx, double inheritedVy, double inheritedVz,
            double projectileSpeed, double friction, double gravity,
            double maxTimeTicks
    ) {
        if (projectileSpeed <= EPSILON || maxTimeTicks <= 0.0D) return Solution.invalid();

        double maxTime = Math.max(SCAN_STEP_TICKS, maxTimeTicks);
        double previousT = 0.0D;
        Evaluation previous = evaluate(
                previousT,
                targetX, targetY, targetZ,
                targetVx, targetVy, targetVz,
                targetAx, targetAy, targetAz,
                inheritedVx, inheritedVy, inheritedVz,
                projectileSpeed, friction, gravity
        );

        double low = 0.0D;
        double high = -1.0D;
        Evaluation highEval = null;

        for (double t = SCAN_STEP_TICKS; t <= maxTime + EPSILON; t += SCAN_STEP_TICKS) {
            double clampedT = Math.min(t, maxTime);
            Evaluation current = evaluate(
                    clampedT,
                    targetX, targetY, targetZ,
                    targetVx, targetVy, targetVz,
                    targetAx, targetAy, targetAz,
                    inheritedVx, inheritedVy, inheritedVz,
                    projectileSpeed, friction, gravity
            );
            if (current.residual <= 0.0D && previous.residual > 0.0D) {
                low = previousT;
                high = clampedT;
                highEval = current;
                break;
            }
            previousT = clampedT;
            previous = current;
            if (clampedT >= maxTime) break;
        }

        if (high < 0.0D || highEval == null) return Solution.invalid();

        Evaluation best = highEval;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) * 0.5D;
            Evaluation eval = evaluate(
                    mid,
                    targetX, targetY, targetZ,
                    targetVx, targetVy, targetVz,
                    targetAx, targetAy, targetAz,
                    inheritedVx, inheritedVy, inheritedVz,
                    projectileSpeed, friction, gravity
            );
            best = eval;
            if (eval.residual > 0.0D) {
                low = mid;
            } else {
                high = mid;
            }
        }

        double solvedT = (low + high) * 0.5D;
        best = evaluate(
                solvedT,
                targetX, targetY, targetZ,
                targetVx, targetVy, targetVz,
                targetAx, targetAy, targetAz,
                inheritedVx, inheritedVy, inheritedVz,
                projectileSpeed, friction, gravity
        );

        return new Solution(
                true,
                solvedT,
                best.aimX, best.aimY, best.aimZ,
                best.predictedX, best.predictedY, best.predictedZ,
                Math.abs(best.residual)
        );
    }

    private static Evaluation evaluate(
            double t,
            double targetX, double targetY, double targetZ,
            double targetVx, double targetVy, double targetVz,
            double targetAx, double targetAy, double targetAz,
            double inheritedVx, double inheritedVy, double inheritedVz,
            double projectileSpeed, double friction, double gravity
    ) {
        double predictedX = targetX + targetVx * t + 0.5D * targetAx * t * t;
        double predictedY = targetY + targetVy * t + 0.5D * targetAy * t * t;
        double predictedZ = targetZ + targetVz * t + 0.5D * targetAz * t * t;

        double travelFactor = dragTravelFactor(friction, t);
        double inheritedX = inheritedVx * travelFactor;
        double inheritedY = inheritedVy * travelFactor;
        double inheritedZ = inheritedVz * travelFactor;
        double gravityY = gravityDisplacementY(gravity, friction, t);

        double aimX = predictedX - inheritedX;
        double aimY = predictedY - inheritedY - gravityY;
        double aimZ = predictedZ - inheritedZ;
        double requiredDistance = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
        double availableDistance = projectileSpeed * travelFactor;

        return new Evaluation(
                requiredDistance - availableDistance,
                aimX, aimY, aimZ,
                predictedX, predictedY, predictedZ
        );
    }

    private static double retention(double friction) {
        double f = Math.max(0.0D, Math.min(0.999999D, friction));
        return 1.0D - f;
    }

    private record Evaluation(
            double residual,
            double aimX, double aimY, double aimZ,
            double predictedX, double predictedY, double predictedZ
    ) {}
}
