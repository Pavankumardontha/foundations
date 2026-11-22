interface TemperatureSensor {
    int readCelsius();
}

class RoofSensor implements TemperatureSensor {
    @Override
    public int readCelsius() {
        // Pretend this reads real hardware on the roof.
        return 31;
    }
}

class FixedSensor implements TemperatureSensor {
    @Override
    public int readCelsius() {
        return -5;
    }
}

class WeatherReport {
    private final TemperatureSensor sensor;

    WeatherReport(TemperatureSensor sensor) {
        this.sensor = sensor;
    }

    String line() {
        int c = sensor.readCelsius();
        return c <= 0 ? "freezing: " + c : "mild: " + c;
    }
}

public class Q1L6 {
    public static void main(String[] args) {
        WeatherReport report = new WeatherReport(new FixedSensor());
        System.out.println(report.line());
    }
}
