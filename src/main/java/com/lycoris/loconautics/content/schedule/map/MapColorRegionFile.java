package com.lycoris.loconautics.content.schedule.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MapColorRegionFile implements AutoCloseable {

    public static final int REGION_SIZE   = 32;
    public static final int CHUNK_SLOTS   = REGION_SIZE * REGION_SIZE;
    public static final int HEADER_BYTES  = CHUNK_SLOTS * 4;
    public static final int PAYLOAD_BYTES = 256;

    private final FileChannel channel;
    private final int[]       offsets = new int[CHUNK_SLOTS];

    public MapColorRegionFile(Path path) throws IOException {
        boolean exists = Files.exists(path);
        this.channel = FileChannel.open(path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);

        if (exists) {
            readHeader();
        } else {
            writeEmptyHeader();
        }
    }

    public void write(int localX, int localZ, byte[] colors) throws IOException {
        if (colors == null || colors.length != PAYLOAD_BYTES)
            throw new IllegalArgumentException("colors must be exactly 256 bytes");

        int slot   = slot(localX, localZ);
        int offset = offsets[slot];

        if (offset == 0) {
            offset = (int) channel.size();
            offsets[slot] = offset;
            writeOffsetEntry(slot, offset);
        }

        ByteBuffer buf = ByteBuffer.wrap(colors);
        int written = 0;
        while (written < PAYLOAD_BYTES) {
            written += channel.write(buf, offset + written);
        }
    }

    public byte[] read(int localX, int localZ) throws IOException {
        int slot   = slot(localX, localZ);
        int offset = offsets[slot];
        if (offset == 0) return null;

        byte[]     colors = new byte[PAYLOAD_BYTES];
        ByteBuffer buf    = ByteBuffer.wrap(colors);
        int read = 0;
        while (read < PAYLOAD_BYTES) {
            int n = channel.read(buf, offset + read);
            if (n < 0) return null;
            read += n;
        }
        return colors;
    }

    public boolean hasChunk(int localX, int localZ) {
        return offsets[slot(localX, localZ)] != 0;
    }

    @Override
    public void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.force(true);
            channel.close();
        }
    }

    private void readHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES);
        channel.read(buf, 0);
        buf.flip();
        for (int i = 0; i < CHUNK_SLOTS; i++) {
            offsets[i] = buf.getInt();
        }
    }

    private void writeEmptyHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES);
        channel.write(buf, 0);
    }

    private void writeOffsetEntry(int slot, int offset) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(offset);
        buf.flip();
        channel.write(buf, (long) slot * 4);
    }

    private static int slot(int localX, int localZ) {
        return localX + localZ * REGION_SIZE;
    }
}
