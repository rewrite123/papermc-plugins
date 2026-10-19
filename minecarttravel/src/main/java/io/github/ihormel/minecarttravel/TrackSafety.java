package io.github.ihormel.minecarttravel;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Minecart;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class TrackSafety {
    static TrackPos railUnder(Location location) {
        var world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int dy = 0; dy >= -1; dy--) {
            int y = location.getBlockY() + dy;
            if (y >= world.getMinHeight() && y < world.getMaxHeight()
                    && world.getBlockAt(x, y, z).getBlockData() instanceof Rail) {
                return new TrackPos(world.getUID(), x, y, z);
            }
        }
        return null;
    }

    static Vector direction(RailShape shape, Vector velocity) {
        if (shape == null || !shape.flatStraight()) return null;
        double value = shape == RailShape.EAST_WEST ? velocity.getX() : velocity.getZ();
        if (Math.abs(value) < 0.01) return null;
        return shape == RailShape.EAST_WEST ? new Vector(Math.signum(value), 0, 0)
                : new Vector(0, 0, Math.signum(value));
    }

    static boolean clear(World world, TrackPos pos, RailShape shape, Vector direction, int lookahead) {
        if (shape == null || !shape.flatStraight() || direction == null) return false;
        for (int offset = 0; offset <= lookahead; offset++) {
            var check = pos.add((int) direction.getX() * offset, 0, (int) direction.getZ() * offset);
            if (!world.isChunkLoaded(check.x() >> 4, check.z() >> 4)) return false;
            var block = world.getBlockAt(check.x(), check.y(), check.z());
            if (!(block.getBlockData() instanceof Rail rail) || RailShape.of(rail) != shape) return false;
            // Do not push through powered-rail brakes or active ejector rails.
            if (block.getType() == Material.POWERED_RAIL && rail instanceof Powerable power && !power.isPowered()) {
                return false;
            }
            if (block.getType() == Material.ACTIVATOR_RAIL && rail instanceof Powerable power && power.isPowered()) {
                return false;
            }
            if (!world.getBlockAt(check.x(), check.y() - 1, check.z()).getType().isSolid()
                    || !safeHeadroom(world.getBlockAt(check.x(), check.y() + 1, check.z()))
                    || !safeHeadroom(world.getBlockAt(check.x(), check.y() + 2, check.z()))) return false;
        }
        return true;
    }

    static Location arrival(World world, RouteTrace.End end) {
        var pos = end.rail();
        return new Location(world, pos.x() + 0.5, pos.y() + 0.1, pos.z() + 0.5);
    }

    static boolean clearOfEntities(Minecart cart, TrackPos pos, Vector direction, int lookahead) {
        double x1 = pos.x() + 0.5;
        double z1 = pos.z() + 0.5;
        double x2 = x1 + direction.getX() * lookahead;
        double z2 = z1 + direction.getZ() * lookahead;
        var corridor = new BoundingBox(Math.min(x1, x2) - 0.6, pos.y(), Math.min(z1, z2) - 0.6,
                Math.max(x1, x2) + 0.6, pos.y() + 2.5, Math.max(z1, z2) + 0.6);
        return cart.getWorld().getNearbyEntities(corridor).stream()
                .noneMatch(entity -> !entity.equals(cart) && !cart.getPassengers().contains(entity));
    }

    private static boolean safeHeadroom(Block block) {
        return block.isPassable() && !block.isLiquid() && switch (block.getType()) {
            case FIRE, SOUL_FIRE, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE, NETHER_PORTAL -> false;
            default -> true;
        };
    }

    static boolean arrivalClear(Minecart cart, RouteTrace.End end) {
        var world = cart.getWorld();
        var pos = end.rail();
        var stop = end.lodestone();
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
                || !world.isChunkLoaded(stop.x() >> 4, stop.z() >> 4)) return false;
        var block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (!(block.getBlockData() instanceof Rail rail) || !RailShape.of(rail).flatStraight()
                || world.getBlockAt(stop.x(), stop.y(), stop.z()).getType() != Material.LODESTONE
                || !world.getBlockAt(pos.x(), pos.y() - 1, pos.z()).getType().isSolid()
                || !safeHeadroom(world.getBlockAt(pos.x(), pos.y() + 1, pos.z()))
                || !safeHeadroom(world.getBlockAt(pos.x(), pos.y() + 2, pos.z()))) return false;
        if (block.getType() == Material.ACTIVATOR_RAIL && rail instanceof Powerable power && power.isPowered()) {
            return false;
        }
        var destination = arrival(world, end);
        if (!world.getWorldBorder().isInside(destination)) return false;
        return world.getNearbyEntities(destination, 0.8, 1.5, 0.8).stream()
                .noneMatch(entity -> !entity.equals(cart) && !cart.getPassengers().contains(entity));
    }
}
