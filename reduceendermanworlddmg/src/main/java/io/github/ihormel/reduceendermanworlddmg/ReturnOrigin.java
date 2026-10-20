package io.github.ihormel.reduceendermanworlddmg;

import java.util.UUID;

record ReturnOrigin(UUID world, int x, int y, int z, String blockData, long due) {
    String encode() {
        return world + "|" + x + "|" + y + "|" + z + "|" + blockData + "|" + due;
    }

    static ReturnOrigin decode(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 6 || parts[4].isBlank()) {
            throw new IllegalArgumentException("Invalid Enderman return record");
        }
        return new ReturnOrigin(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4], Long.parseLong(parts[5]));
    }
}
