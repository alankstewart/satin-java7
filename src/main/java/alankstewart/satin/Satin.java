/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package alankstewart.satin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URISyntaxException;
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

import static alankstewart.satin.Laser.CO2;
import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.Math.*;
import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.unmodifiableList;

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
    private static final Path PATH = Paths.get(System.getProperty("user.dir"));

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
       final Pattern p = Pattern.compile("((md|pi)[a-z]{2}\\.out)\\s+([0-9]{2}.[0-9])\\s+([0-9]+)\\s+(?i:\\2)");
        final List<Laser> laserData = new ArrayList<>();
        try (final Scanner scanner = new Scanner(getDataFilePath("laser.dat"))) {
            while (scanner.hasNextLine()) {
                final Matcher m = p.matcher(scanner.nextLine());
                if (m.matches()) {
                    laserData.add(new Laser(m.group(1), parseFloat(m.group(3)), parseInt(m.group(4)), CO2.valueOf(m.group(2).toUpperCase())));
                }
            }
        }
        return unmodifiableList(laserData);
    }

    private Path getDataFilePath(String fileName) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
    }

    private void process(final List<Integer> inputPowers, final Laser laser) throws IOException {
        final Path path = PATH.resolve(laser.getOutputFile());
        try (BufferedWriter writer = Files.newBufferedWriter(path, defaultCharset(), CREATE, WRITE, TRUNCATE_EXISTING);
             final Formatter formatter = new Formatter(writer)) {
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
            }

            formatter.format("\nEnd date: %s\n", Calendar.getInstance().getTime());
        }
    }

    private List<Gaussian> gaussianCalculation(final int inputPower, final float smallSignalGain) {
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
            for (float r = 0; r <= 0.5f; r += DR) {
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
