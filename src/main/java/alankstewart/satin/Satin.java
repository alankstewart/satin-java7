/*
 * User: Alan K Stewart Date: 27/05/2002 Time: 06:20:51
 */

package alankstewart.satin;

import alankstewart.satin.GaussianLaserBean.Gaussian;

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
        } catch (final IOException | RuntimeException e) {
            LOGGER.severe(e.getMessage());
        } finally {
            LOGGER.info(String.format("The time was %s seconds", satin.getElapsedTime(start, System.nanoTime())));
        }
        System.exit(0);
    }

    private void calculate() throws IOException {
        final ExecutorService threadPool = Executors.newCachedThreadPool();
        final List<Future<Integer>> futures = new ArrayList<>();

        final List<Integer> inputPowers = getInputPowers();
        final List<Laser> laserData = getLaserData();
        for (final Laser laser : laserData) {
            futures.add(threadPool.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    int count = 0;
                    final List<Gaussian> gaussianData = new ArrayList<>();
                    for (final Integer inputPower : inputPowers) {
                        if (gaussianData
                                .addAll(new GaussianLaserBean(inputPower, laser.getSmallSignalGain()).calculateGaussians())) {
                            count++;
                        }
                    }
                    writeToFile(laser, gaussianData);
                    return count;
                }
            }));
        }
        if (monitorFutures(futures) != inputPowers.size() * laserData.size()) {
            throw new IllegalStateException("Failed to complete successfully");
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
