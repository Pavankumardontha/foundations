package com.zenith.holds;

import org.springframework.stereotype.Component;

/**
 * Also needs the TTL. In Lesson 3 this class repeated the string key.
 * Now both consumers share one typed object; the key lives only in YAML.
 */
@Component
public class HoldExpiryNotifier {

    private final HoldProperties properties;

    public HoldExpiryNotifier(HoldProperties properties) {
        this.properties = properties;
    }

    public String reminderLine() {
        return "Reminder scheduled " + properties.ttl().minusMinutes(1).toMinutes()
                + " minutes after hold, 1 minute before expiry";
    }
}
