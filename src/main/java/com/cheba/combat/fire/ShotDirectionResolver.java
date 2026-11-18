package com.cheba.combat.fire;

public final class ShotDirectionResolver {

    private static final double MAX_CLIENT_ANGLE_ERROR_DEGREES = 1.5D;

    private ShotDirectionResolver() {
    }

    public static Result resolve(
            float clientYaw,
            float clientPitch,
            float serverYaw,
            float serverPitch
    ) {
        if (!isValid(serverYaw, serverPitch)) {
            return new Result(
                    0.0F,
                    0.0F,
                    false,
                    false,
                    Double.NaN,
                    "INVALID_SERVER_DIRECTION"
            );
        }

        if (!isValid(clientYaw, clientPitch)) {
            return new Result(
                    serverYaw,
                    serverPitch,
                    false,
                    true,
                    Double.POSITIVE_INFINITY,
                    "INVALID_CLIENT_DIRECTION"
            );
        }

        double yawDifference = Math.abs(
                wrapDegrees(clientYaw - serverYaw)
        );

        double pitchDifference = Math.abs(
                clientPitch - serverPitch
        );

        double totalError = Math.hypot(
                yawDifference,
                pitchDifference
        );

        boolean useClientDirection =
                totalError <= MAX_CLIENT_ANGLE_ERROR_DEGREES;

        return new Result(
                useClientDirection ? clientYaw : serverYaw,
                useClientDirection ? clientPitch : serverPitch,
                useClientDirection,
                true,
                totalError,
                useClientDirection
                        ? "CLIENT_DIRECTION_WITHIN_TOLERANCE"
                        : "SERVER_DIRECTION_OUTSIDE_TOLERANCE"
        );
    }

    private static boolean isValid(float yaw, float pitch) {
        return Float.isFinite(yaw)
                && Float.isFinite(pitch)
                && pitch >= -90.0F
                && pitch <= 90.0F;
    }

    private static double wrapDegrees(double angle) {
        angle %= 360.0D;

        if (angle >= 180.0D) {
            angle -= 360.0D;
        } else if (angle < -180.0D) {
            angle += 360.0D;
        }

        return angle;
    }

    public static final class Result {

        private final float yaw;
        private final float pitch;
        private final boolean clientDirectionAccepted;
        private final boolean valid;
        private final double angularErrorDegrees;
        private final String reason;

        private Result(
                float yaw,
                float pitch,
                boolean clientDirectionAccepted,
                boolean valid,
                double angularErrorDegrees,
                String reason
        ) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.clientDirectionAccepted = clientDirectionAccepted;
            this.valid = valid;
            this.angularErrorDegrees = angularErrorDegrees;
            this.reason = reason;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public boolean isClientDirectionAccepted() {
            return clientDirectionAccepted;
        }

        public boolean isValid() {
            return valid;
        }

        public double getAngularErrorDegrees() {
            return angularErrorDegrees;
        }

        public String getReason() {
            return reason;
        }
    }
}
