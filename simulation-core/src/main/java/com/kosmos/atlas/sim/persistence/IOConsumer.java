package com.kosmos.atlas.sim.persistence;

import java.io.IOException;

/** {@code java.util.function.Consumer} that is allowed to throw {@link IOException}. */
@FunctionalInterface
public interface IOConsumer<T> {
    void accept(T t) throws IOException;
}
