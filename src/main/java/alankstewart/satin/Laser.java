package alankstewart.satin;

public final class Laser {

    public enum CO2 {MD, PI}

    private final String outputFile;
    private final float smallSignalGain;
    private final int dischargePressure;
    private final CO2 carbonDioxide;

    public Laser(final String outputFile, final float smallSignalGain, final int dischargePressure,
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

    @Override
    public String toString() {
        return new StringBuilder().append(outputFile).append("  ").append(smallSignalGain).append("  ")
                .append(dischargePressure).append("  ").append(carbonDioxide.name()).toString();
    }
}
