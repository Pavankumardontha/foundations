package com.zenith.holds;

import java.util.Optional;

/**
 * The service's named need: "I need something that can store and find holds."
 * Any class that implements these three methods satisfies the need.
 */
public interface HoldRepository {

    /** Find the hold with this id. The Optional is empty when there is none. */
    Optional<Hold> findById(long id);

    /** Store this hold. */
    void save(Hold hold);

    /** Remove the hold with this id. */
    void deleteById(long id);
}
