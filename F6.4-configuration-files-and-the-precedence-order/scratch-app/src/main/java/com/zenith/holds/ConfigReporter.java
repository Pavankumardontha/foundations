package com.zenith.holds;

import java.time.Duration;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * One job: read configuration values and turn them into printable lines.
 * The Environment arrives by constructor injection, like any other dependency.
 */
@Component
public class ConfigReporter {

    private final Environment environment;

    public ConfigReporter(Environment environment) {
        this.environment = environment;
    }

    public String ttlLine() {
        return "zenith.hold.ttl = " + environment.getProperty("zenith.hold.ttl");
    }

    public String ttlDecodedLine() {
        Duration ttl = Duration.parse(environment.getProperty("zenith.hold.ttl"));
        return "decoded ttl = " + ttl.toMinutes() + " minutes = " + ttl.getSeconds() + " seconds";
    }

    public String maxSeatsLine() {
        return "zenith.hold.max-seats-per-order = "
                + environment.getProperty("zenith.hold.max-seats-per-order");
    }

    public String providerUrlLine() {
        return "zenith.payments.provider-url = "
                + environment.getProperty("zenith.payments.provider-url");
    }

    public String currenciesLine() {
        return "zenith.payments.supported-currencies = "
                + environment.getProperty("zenith.payments.supported-currencies[0]") + ", "
                + environment.getProperty("zenith.payments.supported-currencies[1]") + ", "
                + environment.getProperty("zenith.payments.supported-currencies[2]");
    }
}
