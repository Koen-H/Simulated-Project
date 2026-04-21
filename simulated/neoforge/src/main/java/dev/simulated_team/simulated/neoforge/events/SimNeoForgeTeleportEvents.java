package dev.simulated_team.simulated.neoforge.events;

import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.util.SimTeleportHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Remaps entity teleport/spawn destinations that fall inside a Sable SubLevel from world-space
 * to the SubLevel's local-space. Without this, cross-mod teleporters (e.g. Mekanism) would leave
 * the entity in main-world coordinates and the contraption would move out from under it.
 *
 * The player's same-dimension path in Mekanism bypasses {@link EntityTeleportEvent}
 * (it calls {@code player.connection.teleport} directly); that case is handled by
 * {@code ServerGamePacketListenerImplMixin}. Rotation for the generic {@link Entity#teleportTo}
 * path is handled by {@code EntityMixin} at TAIL (the event has no rotation setters in 1.21.1).
 */
@EventBusSubscriber(modid = Simulated.MOD_ID)
public final class SimNeoForgeTeleportEvents {

    private SimNeoForgeTeleportEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTeleport(final EntityTeleportEvent event) {
        if (event.isCanceled()) return;
        final Entity entity = event.getEntity();
        if (!(entity.level() instanceof final ServerLevel serverLevel)) return;

        final Set<UUID> guard = SimTeleportHelper.REMAP_GUARD.get();
        if (guard.contains(entity.getUUID())) return;

        final Vec3 target = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        final Vec3 remapped = SimTeleportHelper.worldToLocalIfContained(serverLevel, target);
        if (remapped == target) return;

        event.setTargetX(remapped.x);
        event.setTargetY(remapped.y);
        event.setTargetZ(remapped.z);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (event.isCanceled()) return;
        if (event.loadedFromDisk()) return;
        if (!(event.getLevel() instanceof final ServerLevel serverLevel)) return;

        final Entity entity = event.getEntity();
        final Set<UUID> guard = SimTeleportHelper.REMAP_GUARD.get();
        if (guard.contains(entity.getUUID())) return;

        final Vec3 pos = entity.position();
        final SubLevel containing = SimTeleportHelper.subLevelAt(serverLevel, pos);
        if (containing == null) return;

        if (EntitySubLevelUtil.getTrackingOrVehicleSubLevel(entity) == containing) return;

        guard.add(entity.getUUID());
        try {
            final Vec3 local = containing.logicalPose().transformPositionInverse(pos);
            entity.setPosRaw(local.x, local.y, local.z);

            final float[] localYawPitch = SimTeleportHelper.worldToLocalYawPitch(
                    containing, entity.getYRot(), entity.getXRot());
            entity.setYRot(localYawPitch[0]);
            entity.setXRot(localYawPitch[1]);
            entity.yRotO = localYawPitch[0];
            entity.xRotO = localYawPitch[1];
        } finally {
            guard.remove(entity.getUUID());
        }
    }
}
