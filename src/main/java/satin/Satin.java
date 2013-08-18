/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package satin;

import satin.GaussianLaserBean.Gaussian;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Satin extends AbstractSatin {

    public static void main(final String[] args) {
        final long start = System.nanoTime();
        final Satin satin = new Satin();
        try {
            satin.calculate();
        } catch (final Exception e) {
            LOGGER.severe(e.getMessage());
        } finally {
            LOGGER.info(String.format("The time was %s seconds", satin.getElapsedTime(start, System.nanoTime())));
        }
        System.exit(0);
    }

    private void calculate() throws IOException {
        final ExecutorService threadPool = Executors.newCachedThreadPool();
        final List<Future<Integer>> futures = new ArrayList<>();

        final String outputFilePath = getOutputFilePath();
        final List<Integer> inputPowers = getInputPowers();
        final List<String> laserData = getLaserData();
        for (final String laserDataRecord : laserData) {
            futures.add(threadPool.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    int count = 0;
                    final String[] gainMediumParams = laserDataRecord.split("  ");
                    assert gainMediumParams.length == 4 : "The laser data record must have 4 parameters";
                    final float smallSignalGain = Float.valueOf(gainMediumParams[1]);
                    final List<Gaussian> gaussianData = new ArrayList<>();
                    for (final Integer inputPower : inputPowers) {
                         if (gaussianData.addAll(GaussianLaserBean.getInstance(inputPower, smallSignalGain)
                                .calculateGaussians())) {
                            count++;
                        }
                    }
                    SatinOutput.getInstance(outputFilePath).writeToFile(gainMediumParams, gaussianData);
                    return count;
                }
            }));
        }

        final int total = monitorFutures(futures);
        if (total != (laserData.size() * inputPowers.size())) {
            throw new IllegalStateException("Satin failed to complete");
        }
    }

    private int monitorFutures(final List<Future<Integer>> futures) {
        int total = 0;
        Iterator<Future<Integer>> iterator = futures.iterator();
        while (iterator.hasNext()) {
            final Future<Integer> future = iterator.next();
            if (!future.isCancelled() && future.isDone()) {
                try {
                    total += future.get();
                } catch (final Exception e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
                iterator.remove();
            }
            if (!iterator.hasNext()) {
                iterator = futures.iterator();
            }
        }
        return total;
    }
}