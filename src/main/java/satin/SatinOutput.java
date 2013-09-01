package satin;

import satin.GaussianLaserBean.Gaussian;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;

public final class SatinOutput {

    public void writeToFile(final String outputFilePath, final String[] gainMediumParams, final List<Gaussian> gaussianData) throws IOException {
        final File outputFile = new File(outputFilePath, gainMediumParams[0]);
        try (final Formatter formatter = new Formatter(outputFile)) {
            formatter
                    .format("Start date: %s%n%nGaussian Beam%n%nPressure in Main Discharge = %skPa%nSmall-signal Gain = %s %%%nCO2 via %s%n%nPin\t\tPout\t\tSat. Int.\tln(Pout/Pin)\tPout-Pin%n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)%n", Calendar
                            .getInstance().getTime(), gainMediumParams[2], gainMediumParams[1], gainMediumParams[3]);
            for (final Gaussian gaussian : gaussianData) {
                formatter.format("%s\t\t%s\t\t%s\t\t%s\t\t%s%n", gaussian.getInputPower(), gaussian
                        .getOutputPower(), gaussian.getSaturationIntensity(), gaussian
                        .getLogOutputPowerOverInputPower(), gaussian.getOutputPowerMinusInputPower());
            }
            formatter.format("%nEnd date: %s%n", Calendar.getInstance().getTime());
        }
    }
}
