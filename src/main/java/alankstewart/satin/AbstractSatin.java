package alankstewart.satin;

import alankstewart.satin.GaussianLaserBean.Gaussian;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;

abstract class AbstractSatin {

    static final Logger LOGGER = Logger.getLogger(AbstractSatin.class.getName());

    private enum CO2 {MD, PI}

    List<Integer> getInputPowers() throws IOException {
        final List<Integer> inputPowers = new ArrayList<>();
        try (final InputStream inputStream = getInputStream("/pin.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                inputPowers.add(parseInt(scanner.nextLine()));
            }
            return inputPowers;
        }
    }

    List<Laser> getLaserData() throws IOException {
        final List<Laser> laserData = new ArrayList<>();
        try (final InputStream inputStream = getInputStream("/laser.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                final String[] gainMediumParams = scanner.nextLine().split("  ");
                assert gainMediumParams.length == 4 : "The laser data record must have 4 parameters";
                laserData
                        .add(new Laser(gainMediumParams[0], parseFloat(gainMediumParams[1]),
                                parseInt(gainMediumParams[2]
                                .trim()), CO2.valueOf(gainMediumParams[3])));
            }
            return laserData;
        }
    }

    BigDecimal getElapsedTime(final long start, final long end) {
        return valueOf(end - start).divide(valueOf(1E9), 2, ROUND_HALF_UP);
    }

    void writeToFile(final Laser laser, final List<Gaussian> gaussianData) throws IOException {
        final File outputFile = new File(getOutputFilePath(), laser.getOutputFile());
        try (final Formatter formatter = new Formatter(outputFile)) {
            formatter
                    .format("Start date: %s%n%nGaussian Beam%n%nPressure in Main Discharge = %skPa%nSmall-signal Gain = %s%% %nCO2 via %s%n%nPin\t\tPout\t\tSat. Int.\tln(Pout/Pin)\tPout-Pin%n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)%n", Calendar
                            .getInstance().getTime(), laser.getMainDischargePressure(), laser
                            .getSmallSignalGain(), laser.getCo2().name());
            for (final Gaussian gaussian : gaussianData) {
                formatter.format("%s\t\t%s\t\t%s\t\t%s\t\t%s%n", gaussian.getInputPower(), gaussian
                        .getOutputPower(), gaussian.getSaturationIntensity(), gaussian
                        .getLogOutputPowerDividedByInputPower(), gaussian.getOutputPowerMinusInputPower());
            }
            formatter.format("%nEnd date: %s%n", Calendar.getInstance().getTime());
        }
    }

    private InputStream getInputStream(final String name) {
        return getClass().getResourceAsStream(name);
    }

    private String getOutputFilePath() throws IOException {
        final Properties properties = new Properties();
        try (final InputStream inputStream = getInputStream("/satin.properties")) {
            properties.load(inputStream);
        }
        return properties.getProperty("outputFilePath");
    }

    static class Laser {

        private final String outputFile;
        private final float smallSignalGain;
        private final int mainDischargePressure;
        private final CO2 co2;

        Laser(final String outputFile, final float smallSignalGain, final int mainDischargePressure, final CO2 co2) {
            this.outputFile = outputFile;
            this.smallSignalGain = smallSignalGain;
            this.mainDischargePressure = mainDischargePressure;
            this.co2 = co2;
        }

        public String getOutputFile() {
            return outputFile;
        }

        public float getSmallSignalGain() {
            return smallSignalGain;
        }

        public int getMainDischargePressure() {
            return mainDischargePressure;
        }

        public CO2 getCo2() {
            return co2;
        }
    }
}
