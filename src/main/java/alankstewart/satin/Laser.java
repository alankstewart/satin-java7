package alankstewart.satin;

public class Laser {

    private final String outputFile;
    private final float smallSignalGain;
    private final int dischargePressure;
    private final String carbonDioxide;

    public Laser(final String outputFile, final float smallSignalGain, final int dischargePressure,
                 final String carbonDioxide) {
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

    public String getCarbonDioxide() {
        return carbonDioxide;
    }
}
