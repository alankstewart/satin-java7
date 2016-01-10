/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package alankstewart.satin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Math.*;
import static java.lang.System.nanoTime;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.StandardOpenOption.*;
import static java.util.Collections.unmodifiableList;

final class Satin {

    private static final Path PATH = Paths.get(System.getProperty("user.dir"));
    private static final double RAD = 0.18;
    private static final double RAD2 = pow(RAD, 2);
    private static final double W1 = 0.3;
    private static final double DR = 0.002;
    private static final double DZ = 0.04;
    private static final double LAMBDA = 0.0106;
    private static final double AREA = PI * RAD2;
    private static final double Z1 = PI * pow(W1, 2) / LAMBDA;
    private static final double Z12 = pow(Z1, 2);
    private static final double EXPR = 2 * PI * DR;
    private static final int INCR = 8001;

    public static void main(final String[] args) {
        final long start = nanoTime();
        final Satin satin = new Satin();
        try {
            if (args.length > 0 && args[0].equals("-single")) {
                satin.calculate();
            } else {
                satin.calculateConcurrently();
            }
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        } finally {
            System.out.format("The time was %.3f seconds\n", valueOf(nanoTime() - start).divide(valueOf(1E9), 3, ROUND_HALF_UP));
        }
    }

    private void calculate() throws IOException {
        final List<Integer> inputPowers = getInputPowers();
        for (final Laser laser : getLaserData()) {
            process(inputPowers, laser);
        }
    }

    private void calculateConcurrently() throws ExecutionException, InterruptedException, IOException {
        final List<Integer> inputPowers = getInputPowers();
        final List<Callable<Void>> tasks = new ArrayList<>();
        for (final Laser laser : getLaserData()) {
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    process(inputPowers, laser);
                    return null;
                }
            });
        }
Fo
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            for (final Future<Void> future : executorService.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executorService.shutdown();
        }
    }

    private List<Integer> getInputPowers() throws IOException {
        final List<Integer> inputPowers = new ArrayList<>();
        try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("pin.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextInt()) {
                inputPowers.add(scanner.nextInt());
            }
        }
        return unmodifiableList(inputPowers);
    }

    private List<Laser> getLaserData() throws IOException {
        final Pattern p = Pattern.compile("((md|pi)[a-z]{2}\\.out)\\s+([0-9]{2}\\.[0-9])\\s+([0-9]+)\\s+(?i:\\2)");
        final List<Laser> laserData = new ArrayList<>();
        try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("laser.dat");
             final Scanner scanner = new Scanner(inputStream)) {
            while (scanner.hasNextLine()) {
                final Matcher m = p.matcher(scanner.nextLine());
                if (m.matches()) {
                    laserData.add(new Laser(m.group(1), parseDouble(m.group(3)), parseInt(m.group(4)), m.group(2)));
                }
            }
        }
        return unmodifiableList(laserData);
    }

    private void process(final List<Integer> inputPowers, final Laser laser) throws IOException {
        final Path path = PATH.resolve(laser.getOutputFile());
        final String header = "Start date: %s\n\nGaussian Beam\n\nPressure in Main Discharge = %skPa\nSmall-signal Gain = %s\nCO2 via %s\n\nPin\t\tPout\t\tSat. Int\tln(Pout/Pin\tPout-Pin\n(watts)\t\t(watts)\t\t(watts/cm2)\t\t\t(watts)\n";
        try (BufferedWriter writer = Files.newBufferedWriter(path, defaultCharset(), CREATE, WRITE, TRUNCATE_EXISTING);
             final Formatter formatter = new Formatter(writer)) {
            formatter.format(header,
                    Calendar.getInstance().getTime(),
                    laser.getDischargePressure(),
                    laser.getSmallSignalGain(),
                    laser.getCarbonDioxide());

            for (final int inputPower : inputPowers) {
                for (final Gaussian gaussian : gaussianCalculation(inputPower, laser.getSmallSignalGain())) {
                    formatter.format("%d\t\t%s\t\t%d\t\t%s\t\t%s\n",
                            gaussian.getInputPower(),
                            gaussian.getOutputPower(),
                            gaussian.getSaturationIntensity(),
                            gaussian.getLogOutputPowerDividedByInputPower(),
                            gaussian.getOutputPowerMinusInputPower());
                }
            }

            formatter.format("\nEnd date: %s\n", Calendar.getInstance().getTime());
        }
    }

    List<Gaussian> gaussianCalculation(final int inputPower, final double smallSignalGain) {
        final double[] expr1 = new double[INCR];
        for (int i = 0; i < INCR; i++) {
            final double zInc = ((double) i - INCR / 2) / 25;
            expr1[i] = 2 * zInc * DZ / (Z12 + pow(zInc, 2));
        }
        final double expr2 = smallSignalGain / 32000 * DZ;
        final double inputIntensity = 2 * inputPower / AREA;

        final List<Gaussian> gaussians = new ArrayList<>();
        for (int saturationIntensity = 10000; saturationIntensity <= 25000; saturationIntensity += 1000) {
            final double expr3 = saturationIntensity * expr2;
            double outputPower = 0.0;
            for (double r = 0; r <= 0.5; r += DR) {
                double outputIntensity = inputIntensity * exp(-2 * pow(r, 2) / RAD2);
                for (int j = 0; j < INCR; j++) {
                    outputIntensity *= 1 + expr3 / (saturationIntensity + outputIntensity) - expr1[j];
                }
                outputPower += (outputIntensity * EXPR * r);
            }
            gaussians.add(new Gaussian(inputPower, outputPower, saturationIntensity));
        }
        return unmodifiableList(gaussians);
    }
}
