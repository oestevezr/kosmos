package com.kosmos.atlas.sim.commands;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Append-only binary log of every applied command, tagged with the simulation tick it ran on
 * (spec §47: "snapshot + journal/command log so interrupted saves can recover safely"; spec §35:
 * "reproduce bugs from a seed + command log").
 *
 * <p>{@code seed + generatorVersion + journal replay} deterministically reproduces the exact
 * world state that produced a bug report, without needing to ship a full save. Uses a hand-rolled
 * binary format, never {@code ObjectOutputStream} — see the persistence security notes in
 * {@code docs/architecture.md} for why native Java serialization is banned project-wide.
 */
public final class CommandJournal implements AutoCloseable {

    private static final int MAGIC = 0x41544A4C; // "ATJL"
    private static final int FORMAT_VERSION = 1;

    private final DataOutputStream out;

    public CommandJournal(OutputStream sink) throws IOException {
        this.out = new DataOutputStream(sink);
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
    }

    /** Appends one applied command. Only commands that were {@link CommandResult#ACCEPTED} should be journaled. */
    public synchronized void append(long tick, Command command) throws IOException {
        out.writeLong(tick);
        out.writeInt(command.typeId());
        java.io.ByteArrayOutputStream payloadBuf = new java.io.ByteArrayOutputStream(64);
        DataOutput payloadOut = new DataOutputStream(payloadBuf);
        command.writePayload(payloadOut);
        byte[] payload = payloadBuf.toByteArray();
        out.writeInt(payload.length);
        out.write(payload);
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    /** One journal record as replayed from disk, before being handed to a decoder/applier. */
    public static final class Entry {
        public final long tick;
        public final int typeId;
        public final byte[] payload;

        Entry(long tick, int typeId, byte[] payload) {
            this.tick = tick;
            this.typeId = typeId;
            this.payload = payload;
        }
    }

    /**
     * Replays a journal, decoding each entry via the supplied {@code decoders} (typeId -> decoder)
     * and invoking {@code consumer} in tick order. Throws {@link IOException} on any structural
     * corruption rather than silently skipping bad records.
     */
    public static void replay(InputStream source, Map<Integer, CommandDecoder> decoders,
                               java.util.function.BiConsumer<Long, Command> consumer) throws IOException {
        DataInputStream in = new DataInputStream(source);
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("Not an Atlas command journal (bad magic)");
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported journal format version " + version);
        }
        while (true) {
            long tick;
            try {
                tick = in.readLong();
            } catch (EOFException eof) {
                break;
            }
            int typeId = in.readInt();
            int payloadLength = in.readInt();
            if (payloadLength < 0 || payloadLength > 1 << 20) {
                throw new IOException("Implausible journal payload length: " + payloadLength);
            }
            byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            CommandDecoder decoder = decoders.get(typeId);
            if (decoder == null) {
                throw new IOException("No decoder registered for command typeId " + typeId);
            }
            DataInput payloadIn = new DataInputStream(new java.io.ByteArrayInputStream(payload));
            Command command = decoder.decode(payloadIn);
            consumer.accept(tick, command);
        }
    }

    public static Map<Integer, CommandDecoder> newDecoderRegistry() {
        return new HashMap<>();
    }
}
