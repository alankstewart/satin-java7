package alankstewart.satin;

import alankstewart.satin.GaussianLaserBean.Gaussian;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SatinSingleThread extends AbstractSatin {

    public static void main(final String[] args) {
        final long start = System.nanoTime();
        final SatinSingleThread satin = new SatinSingleThread();
        try {
            satin.calculate();
        } catch (final IOException | RuntimeException e) {
            LOGGER.severe(e.getMessage());
        } finally {
            LOGGER.info(String.format("The time was %s seconds", satin.getElapsedTime(start, System.nanoTime())));
        }
    }

    private void calculate() throws IOException {
        int total = 0;
        final List<Integer> inputPowers = getInputPowers();
        final List<Laser> laserData = getLaserData();
        for (final Laser laser : laserData) {
            final List<Gaussian> gaussianData = new ArrayList<>();
            int count = 0;
            for (final Integer inputPower : inputPowers) {
                if (gaussianData
                        .addAll(new GaussianLaserBean(inputPower, laser.getSmallSignalGain()).calculateGaussians())) {
                    count++;
                }
            }
            writeToFile(laser, gaussianData);
            total += count;
        }
        if (total != inputPowers.size() * laserData.size()) {
            throw new IllegalStateException("Failed to complete successfully");
        }
    }
}
