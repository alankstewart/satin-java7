/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package alankstewart.satin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static alankstewart.satin.Laser.CO2;
import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.Math.PI;
import static java.lang.Math.exp;
import static java.lang.Math.pow;
import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Paths.get;

public final class Satin {

    private static final float RAD = 0.18f;
    private static final float W1 = 0.3f;
    private static final float DR = 0.002f;
    private static final float DZ = 0.04f;
    private static final float LAMDA = 0.0106f;
    private static final double AREA = PI * pow(RAD, 2);
    private static final double Z1 = PI * pow(W1, 2) / LAMDA;
    private static final double Z12 = Z1 * Z1;
    private static final double EXPR = 2 * PI * DR;
    private static final int INCR = 8001;
    private static final String DATA_FILE_PATH;
    private static final String OUTPUT_FILE_PATH;

    static {
        try (final InputStream inputStream = Satin.class.getResourceAsStream("/application.properties")) {
            final Properties properties = new Properties();
            properties.load(inputStream);
            DATA_FILE_PATH = properties.getProperty("dataFilePath");
            OUTPUT_FILE_PATH = properties.getProperty("outputFilePath");
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(final String[] args) {
        final long start = nanoTime();
        final Satin satin = new Satin();
        try {
            if (!satin.calculate(args.length > 0 && args[0].equals("-concurrent"))) {
                out.format("Failed to complete");
            }
        } catch (final IOException | RuntimeException e) {
            out.format(e.getMessage());
        } finally {
            out.format("The time was %s seconds", valueOf(nanoTime() - start)
                    .divide(valueOf(1E9), 3, ROUND_HALF_UP));
        }
    }

    private boolean calculate(final boolean concurrent) throws IOException {
        final List<Integer> inputPowers = getInputPowers();
        final List<Laser> laserData = getLaserData();
        int total = 0;

        if (concurrent) {
            final List<Callable<Integer>> tasks = new ArrayList(laserData.size());
            for (final Laser laser : laserData) {
                tasks.add(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        return process(inputPowers, laser);
                    }
                });
            }
            final ExecutorService executorService = Executors.newCachedThreadPool();
            try {
                for (final Future<Integer> future : executorService.invokeAll(tasks)) {
                    total += future.get();
                }
            } catch (final InterruptedException | ExecutionException e) {
                throw new IllegalStateException(e);
            } finally {
                executorService.shutdown();
            }
        } else {
            for (final Laser laser : laserData) {
                total += process(inputPowers, laser);
            }
        }
        return total == laserData.size() * inputPowers.size();
    }

    private List<Integer> getInputPowers() throws IOException {
        final List<String> lines = readAllLines(get(DATA_FILE_PATH, "pin.dat"), defaultCharset());
        final List<Integer> inputPowers = new ArrayList(lines.size());
        for (final String line : lines) {
            inputPowers.add(parseInt(line));
        }
        return inputPowers;
    }

    private List<Laser> getLaserData() throws IOException {
        final List<String> lines = readAllLines(get(DATA_FILE_PATH, "laser.dat"), defaultCharset());
        final List<Laser> laserData = new ArrayList<>(lines.size());
        for (final String line : lines) {
            final String[] gainMediumParams = line.split("  ");
            assert gainMediumParams.length == 4 : "The laser data record must have 4 parameters";
            laserData.add(new Laser(gainMediumParams[0], parseFloat(gainMediumParams[1]
                    .trim()), parseInt(gainMediumParams[2].trim()), CO2.valueOf(gainMediumParams[3].trim())));
        }
        return laserData;
    }

    private int process(final List<Integer> inputPowers, final Laser laser) throws IOException {
        final Path path = get(OUTPUT_FILE_PATH, laser.getOutputFile());
        deleteIfExists(path);
        int count = 0;
        try (final Formatter formatter = new Formatter(createFile(path).toFile())) {
            formatter
                    .format("Start date: %s\n\nGaussian Beam\n\nPressure in Main Discharge = %skPa\nSmall-signal Gain = %s\nCO2 via %s\n\nPin\t\tPout\t\tSat. Int.\tln(Pout/Pin)\tPout-Pin\n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)\n", Calendar
                            .getInstance().getTime(), laser.getDischargePressure(), laser.getSmallSignalGain(), laser
                            .getCarbonDioxide().name());
            for (final int inputPower : inputPowers) {
                for (final Gaussian gaussian : gaussianCalculation(inputPower, laser.getSmallSignalGain())) {
                    formatter.format("%s\t\t%s\t\t%s\t\t%s\t\t%s\n", gaussian.getInputPower(), gaussian
                            .getOutputPower(), gaussian.getSaturationIntensity(), gaussian
                            .getLogOutputPowerDividedByInputPower(), gaussian.getOutputPowerMinusInputPower());
                }
                count++;
            }
            formatter.format("\nEnd date: %s\n", Calendar.getInstance().getTime());
        }
        return count;
    }

    private List<Gaussian> gaussianCalculation(final int inputPower, final float smallSignalGain) {
        final List<Gaussian> gaussians = new ArrayList<>();

        final double[] expr1 = new double[INCR];
        for (int i = 0; i < INCR; i++) {
            final double zInc = ((double) i - 4000) / 25;
            expr1[i] = 2 * zInc * DZ / (Z12 + pow(zInc, 2));
        }

        final double inputIntensity = 2 * inputPower / AREA;
        final double expr2 = (smallSignalGain / 32E3) * DZ;

        for (int saturationIntensity = 10000; saturationIntensity <= 25000; saturationIntensity += 1000) {
            double outputPower = 0.0;
            final double expr3 = saturationIntensity * expr2;
            for (float r = 0; r <= 0.5f; r += DR) {
                double outputIntensity = inputIntensity * exp(-2 * pow(r, 2) / pow(RAD, 2));
                for (int j = 0; j < INCR; j++) {
                    outputIntensity *= (1 + expr3 / (saturationIntensity + outputIntensity) - expr1[j]);
                }
                outputPower += (outputIntensity * EXPR * r);
            }
            gaussians.add(new Gaussian(inputPower, outputPower, saturationIntensity));
        }

        return gaussians;
    }
}
