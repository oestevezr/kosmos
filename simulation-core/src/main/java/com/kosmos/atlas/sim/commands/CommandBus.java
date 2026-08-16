package com.kosmos.atlas.sim.commands;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-producer/single-consumer ring buffer carrying {@link Command}s from the client thread
 * to the simulation thread (spec §40: "command queues... Avoid coarse global locks").
 *
 * <p>Backed by a fixed-size array of references; {@link #offer(Command)} and {@link #poll()}
 * only touch atomics and array slots, never allocate or block. Capacity must be a power of two.
 */
public final class CommandBus {

    private final Command[] buffer;
    private final int mask;
    private final AtomicInteger head = new AtomicInteger(0); // consumer reads here
    private final AtomicInteger tail = new AtomicInteger(0); // producer writes here

    public CommandBus(int capacityPowerOfTwo) {
        if (Integer.bitCount(capacityPowerOfTwo) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two, was " + capacityPowerOfTwo);
        }
        this.buffer = new Command[capacityPowerOfTwo];
        this.mask = capacityPowerOfTwo - 1;
    }

    /** Called from the producer (client) thread only. Returns false if the bus is full. */
    public boolean offer(Command command) {
        int t = tail.get();
        int h = head.get();
        if (t - h >= buffer.length) {
            return false; // full — caller should surface backpressure rather than block a thread
        }
        buffer[t & mask] = command;
        tail.lazySet(t + 1);
        return true;
    }

    /** Called from the consumer (simulation) thread only. Returns null if empty. */
    public Command poll() {
        int h = head.get();
        if (h == tail.get()) {
            return null;
        }
        Command command = buffer[h & mask];
        buffer[h & mask] = null;
        head.lazySet(h + 1);
        return command;
    }

    public int size() {
        return tail.get() - head.get();
    }
}
