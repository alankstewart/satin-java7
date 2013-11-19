package alankstewart.satin;

import alankstewart.satin.GaussianLaserBean.Gaussian;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Paths.get;

abstract class AbstractSatin {

    static final Logger LOGGER = Logger.getLogger(AbstractSatin.class.getName());

    private static final String DATA_FILE_PATH = "dataFilePath";

    private static Properties properties = new Properties();

    private enum CO2 {MD, PI}

    static {
        try (final InputStream inputStream = AbstractSatin.class.getResourceAsStream("/satin.properties")) {
            properties.load(inputStream);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    List<Integer> getInputPowers() throws IOException {
        final List<Integer> inputPowers = new ArrayList<>();
        for (final String line : readAllLines(get(properties
                .getProperty(DATA_FILE_PATH), "pin.dat"), defaultCharset())) {
            inputPowers.add(parseInt(line));
        }
        return inputPowers;
    }

    List<Laser> getLaserData() throws IOException {
        final List<Laser> laserData = new ArrayList<>();
        for (final String line : readAllLines(get(properties
                .getProperty(DATA_FILE_PATH), "laser.dat"), defaultCharset())) {
            final String[] gainMediumParams = line.split("  ");
            assert gainMediumParams.length == 4 : "The laser data record must have 4 parameters";
            laserData.add(new Laser(gainMediumParams[0], parseFloat(gainMediumParams[1]), parseInt(gainMediumParams[2]
                    .trim()), CO2.valueOf(gainMediumParams[3])));
        }
        return laserData;
    }

    BigDecimal getElapsedTime(final long start, final long end) {
        return valueOf(end - start).divide(valueOf(1E9), 2, ROUND_HALF_UP);
    }

    void writeToFile(final Laser laser, final List<Gaussian> gaussianData) throws IOException {
        final Path path = get(properties.getProperty("outputFilePath"), laser.getOutputFile());
        deleteIfExists(path);
        try (final Formatter formatter = new Formatter(createFile(path).toFile())) {
            formatter
                    .format("Start date: %s%n%nGaussian Beam%n%nPressure in Main Discharge = %skPa%nSmall-signal Gain = %s%% %nCO2 via %s%n%nPin\t\tPout\t\tSat. Int.\tln(Pout/Pin)\tPout-Pin%n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)%n", Calendar
                            .getInstance().getTime(), laser.getDischargePressure(), laser.getSmallSignalGain(), laser
                            .getCarbonDioxide().name());
            for (final Gaussian gaussian : gaussianData) {
                formatter.format("%s\t\t%s\t\t%s\t\t%s\t\t%s%n", gaussian.getInputPower(), gaussian
                        .getOutputPower(), gaussian.getSaturationIntensity(), gaussian
                        .getLogOutputPowerDividedByInputPower(), gaussian.getOutputPowerMinusInputPower());
            }
            formatter.format("%nEnd date: %s%n", Calendar.getInstance().getTime());
        }
    }

    static class Laser {

        private final String outputFile;
        private final float smallSignalGain;
        private final int dischargePressure;
        private final CO2 carbonDioxide;

        Laser(final String outputFile, final float smallSignalGain, final int dischargePressure,
              final CO2 carbonDioxide) {
            this.outputFile = outputFile;
            this.smallSignalGain = smallSignalGain;
            this.dischargePressure = dischargePressure;
            this.carbonDioxide = carbonDioxide;
        }

        public String getOutputFile() {
            return outputFile;
        }

        public float getSmallSignalGain() {
            return smallSignalGain;
        }

        public int getDischargePressure() {
            return dischargePressure;
        }

        public CO2 getCarbonDioxide() {
            return carbonDioxide;
        }
    }
}
