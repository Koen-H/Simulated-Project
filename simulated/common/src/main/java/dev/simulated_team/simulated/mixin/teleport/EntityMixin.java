package dev.simulated_team.simulated.mixin.teleport;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimTeleportHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * Rotation-only tail-hook for {@link Entity#teleportTo(double, double, double)}.
 * Position is already remapped by the {@code EntityTeleportEvent} listener (which vanilla fires
 * inside {@code teleportTo}); here we additionally remap yaw/pitch into the destination SubLevel's
 * local frame so entities face the expected direction relative to the moving contraption.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("TAIL"))
    private void simulated$remapRotationIntoSubLevel(final double x, final double y, final double z,
                                                     final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof final ServerLevel serverLevel)) return;

        final UUID uuid = self.getUUID();
        final Set<UUID> guard = SimTeleportHelper.REMAP_GUARD.get();
        if (guard.contains(uuid)) return;

        final Vec3 pos = self.position();
        final SubLevel subLevel = SimTeleportHelper.subLevelAt(serverLevel, pos);
        if (subLevel == null) return;

        final float[] localYawPitch = SimTeleportHelper.worldToLocalYawPitch(subLevel, self.getYRot(), self.getXRot());
        self.setYRot(localYawPitch[0]);
        self.setXRot(localYawPitch[1]);
        self.yRotO = localYawPitch[0];
        self.xRotO = localYawPitch[1];
    }
}
