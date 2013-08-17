package satin;

import satin.GaussianLaserBean.Gaussian;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SatinSingleThread extends AbstractSatin {

    public static void main(final String[] args) {
        final long start = System.nanoTime();
        final SatinSingleThread satin = new SatinSingleThread();
        try {
            satin.calculate();
        } catch (final IOException e) {
            LOGGER.severe(e.getMessage());
        } finally {
            LOGGER.info(String.format("The time was %s seconds", satin.getElapsedTime(start, System.nanoTime())));
        }
    }

    private void calculate() throws IOException {
        int total = 0;
        final List<Integer> inputPowers = getInputPowers();
        final List<String> laserData = getLaserData();
        final SatinOutput satinOutput = SatinOutput.getInstance(getOutputFilePath());
        for (final String laserDataRecord : laserData) {
            final String[] gainMediumParams = laserDataRecord.split("  ");
            assert gainMediumParams.length == 4 : "The laser data record must have 4 parameters";
            final List<Gaussian> gaussianData = new ArrayList<>();
            int count = 0;
            for (final Integer inputPower : inputPowers) {
                final Float smallSignalGain = Float.valueOf(gainMediumParams[1]);
                if (gaussianData
                        .addAll(GaussianLaserBean.getInstance(inputPower, smallSignalGain).calculateGaussians())) {
                    count++;
                }
            }
            satinOutput.writeToFile(gainMediumParams, gaussianData);
            total += count;
        }

        if (total != (laserData.size() * inputPowers.size())) {
            throw new IllegalStateException("SatinSingleThread failed to complete");
        }
    }
}