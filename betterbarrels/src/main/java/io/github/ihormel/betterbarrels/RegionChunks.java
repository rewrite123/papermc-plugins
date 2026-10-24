package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

final class RegionChunks {
    private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    record Position(int x, int z) {}

    static boolean isRegion(Path path) {
        return NAME.matcher(path.getFileName().toString()).matches();
    }

    static boolean[] allocated(Path path) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4096);
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ)) {
            while (header.hasRemaining()) {
                if (file.read(header) < 0) throw new IOException("Truncated region header: " + path);
            }
        }
        header.flip();
        boolean[] result = new boolean[1024];
        for (int slot = 0; slot < result.length; slot++) {
            int location = header.getInt();
            if (location != 0 && ((location >>> 8) < 2 || (location & 255) == 0)) {
                throw new IOException("Invalid chunk allocation at slot " + slot + " in " + path);
            }
            result[slot] = (location >>> 8) >= 2 && (location & 255) != 0;
        }
        return result;
    }

    static Position position(Path path, int slot) {
        var match = NAME.matcher(path.getFileName().toString());
        if (!match.matches() || slot < 0 || slot >= 1024) {
            throw new IllegalArgumentException("Invalid region path or slot: " + path + " / " + slot);
        }
        int x = Math.addExact(Math.multiplyExact(Integer.parseInt(match.group(1)), 32), slot % 32);
        int z = Math.addExact(Math.multiplyExact(Integer.parseInt(match.group(2)), 32), slot / 32);
        return new Position(x, z);
    }

    private RegionChunks() {}
}
