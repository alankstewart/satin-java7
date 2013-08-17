package satin;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    String getOutputFilePath() throws IOException {
        final Properties properties = new Properties();
        try (final InputStream inputStream = getInputStream("/satin.properties")) {
            properties.load(inputStream);
        }
        return properties.getProperty("outputFilePath");
    }

    BigDecimal getElapsedTime(final long start, final long end) {
        return valueOf(end - start).divide(valueOf(1E9), 2, ROUND_HALF_UP);
    }

    private InputStream getInputStream(final String name) {
        return getClass().getResourceAsStream(name);
    }
}
