package io.github.ihormel.endercraftbuildings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Block layout in local coordinates: x runs to the builder's right, z runs away from the builder, y runs up.
 * Block data strings are stored as if the builder faced north.
 */
record Blueprint(int width, int height, int length, List<String> palette, int[] cells) {
    static final int MAX_SIZE = 32;
    static final int EMPTY = -1;
    private static final int FORMAT = 1;

    Blueprint {
        if (width < 1 || height < 1 || length < 1 || width > MAX_SIZE || height > MAX_SIZE || length > MAX_SIZE) {
            throw new IllegalArgumentException("Blueprint size out of range");
        }
        palette = List.copyOf(palette);
        if (cells.length != width * height * length) throw new IllegalArgumentException("Cell count mismatch");
        for (int cell : cells) {
            if (cell < EMPTY || cell >= palette.size()) throw new IllegalArgumentException("Bad palette index");
        }
        cells = cells.clone();
    }

    int index(int x, int y, int z) { return (y * length + z) * width + x; }

    int cell(int x, int y, int z) { return cells[index(x, y, z)]; }

    int blockCount() {
        int count = 0;
        for (int cell : cells) if (cell != EMPTY) count++;
        return count;
    }

    @Override
    public int[] cells() { return cells.clone(); }

    byte[] encode() {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(FORMAT);
            out.writeByte(width);
            out.writeByte(height);
            out.writeByte(length);
            writeVarInt(out, palette.size());
            for (String entry : palette) out.writeUTF(entry);
            for (int cell : cells) writeVarInt(out, cell + 1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    static Blueprint decode(byte[] data) {
        try (var in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if (in.readUnsignedByte() != FORMAT) throw new IllegalArgumentException("Unknown blueprint format");
            int width = in.readUnsignedByte();
            int height = in.readUnsignedByte();
            int length = in.readUnsignedByte();
            int paletteSize = readVarInt(in);
            if (paletteSize < 0 || paletteSize > width * height * length) {
                throw new IllegalArgumentException("Bad palette size");
            }
            var palette = new ArrayList<String>(paletteSize);
            for (int i = 0; i < paletteSize; i++) palette.add(in.readUTF());
            int[] cells = new int[width * height * length];
            for (int i = 0; i < cells.length; i++) cells[i] = readVarInt(in) - 1;
            return new Blueprint(width, height, length, palette, cells);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Corrupt blueprint", exception);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = in.readUnsignedByte();
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Blueprint that && width == that.width && height == that.height
                && length == that.length && palette.equals(that.palette) && Arrays.equals(cells, that.cells);
    }

    @Override
    public int hashCode() { return 31 * palette.hashCode() + Arrays.hashCode(cells); }
}
