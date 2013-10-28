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

import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;

abstract class AbstractSatin {

    static final Logger LOGGER = Logger.getLogger(AbstractSatin.class.getName());

    List<Integer> getInputPowers() throws IOException {
        final List<Integer> inputPowers = new ArrayList<>();
        try (final InputStream inputStream = getInputStream("/pin.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                inputPowers.add(Integer.parseInt(scanner.nextLine()));
            }
            return inputPowers;
        }
    }

    List<String> getLaserData() throws IOException {
        final List<String> laserData = new ArrayList<>();
        try (final InputStream inputStream = getInputStream("/laser.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                laserData.add(scanner.nextLine());
            }
            return laserData;
        }
    }

    BigDecimal getElapsedTime(final long start, final long end) {
        return valueOf(end - start).divide(valueOf(1E9), 2, ROUND_HALF_UP);
    }

    void writeToFile(final String[] gainMediumParams, final List<Gaussian> gaussianData) throws IOException {
        final File outputFile = new File(getOutputFilePath(), gainMediumParams[0]);
        try (final Formatter formatter = new Formatter(outputFile)) {
            formatter
                    .format("Start date: %s%n%nGaussian Beam%n%nPressure in Main Discharge = %skPa%nSmall-signal Gain = %s %%%nCO2 via %s%n%nPin\t\tPout\t\tSat. Int.\tln(Pout/Pin)\tPout-Pin%n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)%n", Calendar
                            .getInstance().getTime(), gainMediumParams[2], gainMediumParams[1], gainMediumParams[3]);
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
}
