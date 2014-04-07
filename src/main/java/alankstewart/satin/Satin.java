/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package alankstewart.satin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.PI;
import static java.lang.Math.exp;
import static java.lang.Math.pow;
import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

public final class Satin {

    private static final double RAD = 0.18;
    private static final double W1 = 0.3;
    private static final double DR = 0.002;
    private static final double DZ = 0.04;
    private static final double LAMDA = 0.0106;
    private static final double AREA = PI * pow(RAD, 2);
    private static final double Z1 = PI * pow(W1, 2) / LAMDA;
    private static final double Z12 = Z1 * Z1;
    private static final double EXPR = 2 * PI * DR;
    private static final int INCR = 8001;
    private static final Path PATH = Paths.get(System.getProperty("user.dir"));
    private static final Pattern PATTERN = Pattern.compile("((md|pi)[a-z]{2}\\.out)\\s+([0-9]{2}\\.[0-9])\\s+([0-9]+)\\s+(?i:\\2)");

    public static void main(final String[] args) {
        final long start = nanoTime();
        final Satin satin = new Satin();
        try {
            if (args.length > 0 && args[0].equals("-concurrent")) {
                satin.calculateConcurrently();
            } else {
                satin.calculate();
            }
        } catch (final Exception e) {
            out.format("Failed to complete: %s\n", e.getMessage());
        } finally {
            out.format("The time was %s seconds\n", valueOf(nanoTime() - start).divide(valueOf(1E9), 3, ROUND_HALF_UP));
        }
    }

    private void calculateConcurrently() throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        final List<Integer> inputPowers = getInputPowers();
        final List<Laser> laserData = getLaserData();

        final List<Callable<Void>> tasks = new ArrayList<>(laserData.size());
        for (final Laser laser : laserData) {
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    process(inputPowers, laser);
                    return null;
                }
            });
        }

        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            for (final Future<Void> future : executorService.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executorService.shutdown();
        }
    }

    private void calculate() throws IOException, URISyntaxException {
        final List<Integer> inputPowers = getInputPowers();
        final List<Laser> laserData = getLaserData();

        for (final Laser laser : laserData) {
            process(inputPowers, laser);
        }
    }

    private List<Integer> getInputPowers() throws IOException, URISyntaxException {
        final List<Integer> inputPowers = new ArrayList<>();
        try (final Scanner scanner = new Scanner(getDataFilePath("pin.dat"))) {
            while (scanner.hasNextInt()) {
                inputPowers.add(scanner.nextInt());
            }
        }
        return unmodifiableList(inputPowers);
    }

    private List<Laser> getLaserData() throws IOException, URISyntaxException {
        final List<Laser> laserData = new ArrayList<>();
        try (final Scanner scanner = new Scanner(getDataFilePath("laser.dat"))) {
            while (scanner.hasNextLine()) {
                final Matcher m = PATTERN.matcher(scanner.nextLine());
                if (m.matches()) {
                    laserData.add(new Laser(m.group(1), m.group(3), m.group(4), m.group(2)));
                }
            }
        }
        return unmodifiableList(laserData);
    }

    private Path getDataFilePath(String fileName) throws URISyntaxException {
        final URL resource = getClass().getClassLoader().getResource(fileName);
        requireNonNull(resource, "Failed to find resource " + fileName);
        return Paths.get(resource.toURI());
    }

    private void process(final List<Integer> inputPowers, final Laser laser) {
        final Path path = PATH.resolve(laser.getOutputFile());
        try (BufferedWriter writer = Files.newBufferedWriter(path, defaultCharset(), CREATE, WRITE, TRUNCATE_EXISTING);
             final Formatter formatter = new Formatter(writer)) {
            formatter.format(getHeader(), Calendar.getInstance().getTime(), laser.getDischargePressure(),
                    laser.getSmallSignalGain(), laser.getCarbonDioxide().name());

            for (final int inputPower : inputPowers) {
                for (final Gaussian gaussian : gaussianCalculation(inputPower, laser.getSmallSignalGain())) {
                    formatter.format("%s\t\t%s\t\t%s\t\t%s\t\t%s\n", gaussian.getInputPower(), gaussian
                            .getOutputPower(), gaussian.getSaturationIntensity(), gaussian
                            .getLogOutputPowerDividedByInputPower(), gaussian.getOutputPowerMinusInputPower());
                }
            }

            formatter.format("\nEnd date: %s\n", Calendar.getInstance().getTime());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getHeader() {
        return new StringBuilder()
                .append("Start date: %s\n\n")
                .append("Gaussian Beam\n\n")
                .append("Pressure in Main Discharge = %skPa\n")
                .append("Small-signal Gain = %s\n")
                .append("CO2 via %s\n\n")
                .append("Pin\t\tPout\t\tSat. Int\tln(Pout/Pin\tPout-Pin\n")
                .append("(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)\n")
                .toString();
    }

    private List<Gaussian> gaussianCalculation(final int inputPower, final double smallSignalGain) {
        final List<Gaussian> gaussians = new ArrayList<>();

        final double[] expr1 = new double[INCR];
        for (int i = 0; i < INCR; i++) {
            final double zInc = ((double) i - INCR / 2) / 25;
            expr1[i] = 2 * zInc * DZ / (Z12 + pow(zInc, 2));
        }

        final double inputIntensity = 2 * inputPower / AREA;
        final double expr2 = (smallSignalGain / 32E3) * DZ;

        for (int saturationIntensity = 10000; saturationIntensity <= 25000; saturationIntensity += 1000) {
            double outputPower = 0.0;
            final double expr3 = saturationIntensity * expr2;
            for (double r = 0; r <= 0.5; r += DR) {
                double outputIntensity = inputIntensity * exp(-2 * pow(r, 2) / pow(RAD, 2));
                for (int j = 0; j < INCR; j++) {
                    outputIntensity *= (1 + expr3 / (saturationIntensity + outputIntensity) - expr1[j]);
                }
                outputPower += (outputIntensity * EXPR * r);
            }
            gaussians.add(new Gaussian(inputPower, outputPower, saturationIntensity));
        }

        return unmodifiableList(gaussians);
    }
}
