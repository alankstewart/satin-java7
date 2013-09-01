/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package satin;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;

public final class GaussianLaserBean {

    private static final float RAD = 0.18f;
    private static final float W1 = 0.3f;
    private static final float DR = 0.002f;
    private static final float DZ = 0.04f;
    private static final float LAMDA = 0.0106f;
    private static final double AREA = PI * pow(RAD, 2);
    private static final double Z1 = PI * pow(W1, 2) / LAMDA;
    private static final double Z12 = Z1 * Z1;
    private static final double EXPR = 2 * PI * DR;

    public List<Gaussian> calculateGaussians(final int inputPower, final float smallSignalGain) {
        final List<Gaussian> gaussians = new ArrayList<>();

        final double[] expr1 = new double[8 * 8001];
        for (int i = 0; i < 8001; i++) {
            final double zInc = ((double) i - 4000) / 25;
            expr1[i] = 2 * zInc * DZ / (Z12 + pow(zInc, 2));
        }

        final double inputIntensity = 2 * inputPower / AREA;
        final double expr2 = (smallSignalGain / 32E3) * DZ;

        for (int saturationIntensity = 10000; saturationIntensity <= 25000; saturationIntensity += 1000) {
            double outputPower = 0.0;
            final double expr3 = saturationIntensity * expr2;
            for (float r = 0; r <= 0.5; r += DR) {
                double outputIntensity = inputIntensity * exp(-2 * pow(r, 2) / pow(RAD, 2));
                for (int j = 0; j < 8001; j++) {
                    outputIntensity *= (1 + expr3 / (saturationIntensity + outputIntensity) - expr1[j]);
                }
                outputPower += (outputIntensity * EXPR * r);
            }
            gaussians.add(new Gaussian(inputPower, outputPower, saturationIntensity));
        }

        return gaussians;
    }

    static final class Gaussian {

        private final int inputPower;
        private final double outputPower;
        private final int saturationIntensity;

        Gaussian(final int inputPower, final double outputPower, final int saturationIntensity) {
            this.inputPower = inputPower;
            this.outputPower = outputPower;
            this.saturationIntensity = saturationIntensity;
        }

        public BigInteger getInputPower() {
            return BigInteger.valueOf(inputPower);
        }

        public BigDecimal getOutputPower() {
            return valueOf(outputPower).setScale(3, ROUND_HALF_UP);
        }

        public BigInteger getSaturationIntensity() {
            return BigInteger.valueOf(saturationIntensity);
        }

        public BigDecimal getLogOutputPowerOverInputPower() {
            return valueOf(log(outputPower / inputPower)).setScale(3, ROUND_HALF_UP);
        }

        public BigDecimal getOutputPowerMinusInputPower() {
            return valueOf(outputPower).subtract(valueOf(inputPower)).setScale(3, ROUND_HALF_UP);
        }
    }
}