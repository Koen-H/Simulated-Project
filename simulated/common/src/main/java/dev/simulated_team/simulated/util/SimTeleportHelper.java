package dev.simulated_team.simulated.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Remaps teleport destinations that land inside a Sable SubLevel (physics contraption) from
 * world-space to the SubLevel's local-space so the entity is picked up by the contraption's
 * tracking system instead of being left behind in the main world.
 */
public final class SimTeleportHelper {

    /**
     * Reentrancy guard. Shared by the NeoForge event handlers and the mixins so that a remap
     * that triggers a nested teleport call does not recurse.
     */
    public static final ThreadLocal<Set<UUID>> REMAP_GUARD = ThreadLocal.withInitial(HashSet::new);

    private SimTeleportHelper() {}

    @Nullable
    public static SubLevel subLevelAt(final ServerLevel level, final Vec3 worldPos) {
        return Sable.HELPER.getContaining(level, worldPos);
    }

    /**
     * If {@code worldPos} falls inside a SubLevel, returns the SubLevel-local position. Otherwise
     * returns the input {@code worldPos} by reference-identity so callers can detect the
     * pass-through case with {@code ==}.
     */
    public static Vec3 worldToLocalIfContained(final ServerLevel level, final Vec3 worldPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, worldPos);
        if (subLevel == null) return worldPos;
        return subLevel.logicalPose().transformPositionInverse(worldPos);
    }

    /**
     * Remaps a world-space yaw/pitch into the local frame of {@code subLevel} by rotating a unit
     * look-vector by the inverse of the SubLevel's orientation, then re-extracting Euler angles
     * using Minecraft's convention:
     *   look.x = -cos(pitch) * sin(yaw)
     *   look.y = -sin(pitch)
     *   look.z =  cos(pitch) * cos(yaw)
     */
    public static float[] worldToLocalYawPitch(final SubLevel subLevel, final float yaw, final float pitch) {
        final double yawRad = Math.toRadians(yaw);
        final double pitchRad = Math.toRadians(pitch);
        final double cosPitch = Math.cos(pitchRad);
        final Vector3d look = new Vector3d(
                -cosPitch * Math.sin(yawRad),
                -Math.sin(pitchRad),
                 cosPitch * Math.cos(yawRad));
        subLevel.logicalPose().orientation().transformInverse(look);
        final float newPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, look.y))));
        final float newYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        return new float[] { newYaw, newPitch };
    }
}
