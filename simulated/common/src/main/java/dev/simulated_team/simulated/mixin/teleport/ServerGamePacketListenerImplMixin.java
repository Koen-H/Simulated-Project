package dev.simulated_team.simulated.mixin.teleport;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimTeleportHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * Mekanism's same-dimension player teleport calls {@code player.connection.teleport(x, y, z, yaw, pitch)}
 * directly, which does not fire {@code EntityTeleportEvent}. We intercept that 5-arg overload here,
 * remap the destination into a SubLevel's local space if applicable, cancel the original call, and
 * re-invoke with the remapped arguments (guarded against recursion).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Shadow public abstract void teleport(double x, double y, double z, float yaw, float pitch);

    @Inject(method = "teleport(DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void simulated$remapTeleportIntoSubLevel(final double x, final double y, final double z,
                                                     final float yaw, final float pitch,
                                                     final CallbackInfo ci) {
        final ServerPlayer serverPlayer = this.player;
        if (serverPlayer == null) return;

        final UUID uuid = serverPlayer.getUUID();
        final Set<UUID> guard = SimTeleportHelper.REMAP_GUARD.get();
        if (guard.contains(uuid)) return;

        final ServerLevel level = serverPlayer.serverLevel();
        final Vec3 target = new Vec3(x, y, z);
        final SubLevel subLevel = SimTeleportHelper.subLevelAt(level, target);
        if (subLevel == null) return;

        final Vec3 local = subLevel.logicalPose().transformPositionInverse(target);
        final float[] localYawPitch = SimTeleportHelper.worldToLocalYawPitch(subLevel, yaw, pitch);

        ci.cancel();
        guard.add(uuid);
        try {
            this.teleport(local.x, local.y, local.z, localYawPitch[0], localYawPitch[1]);
        } finally {
            guard.remove(uuid);
        }
    }
}
