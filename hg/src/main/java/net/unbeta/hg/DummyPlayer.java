package net.unbeta.hg;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * A dummy player for testing: a real player as far as the server is concerned (it is in the
 * player list, counts as in or out, takes damage, dies, drops its things and loses hearts),
 * but its "brain" just wanders about - walking, stopping, turning, hopping over steps. When it
 * dies it gets back up at spawn after a second, as a player pressing Respawn would.
 */
public final class DummyPlayer extends ServerPlayerEntity {

    private int nextChange;
    private boolean walking;

    public DummyPlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
        super(server, world, profile);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) return;
        if (this.isDead()) {
            // Get up before the 20th tick of dying: at 20 the game takes a dead player's body out
            // of the world (a real player is then rebuilt by the Respawn button).
            if (this.deathTime >= 15) respawnHere();
        } else {
            wander();
        }
        this.playerTick();
    }

    private void wander() {
        if (--nextChange <= 0) {
            nextChange = 30 + this.random.nextInt(70);
            walking = this.random.nextInt(3) != 0;
            float yaw = this.getYaw() + (this.random.nextFloat() - 0.5F) * 160F;
            this.setYaw(yaw);
            this.setHeadYaw(yaw);
            this.setBodyYaw(yaw);
        }
        this.forwardSpeed = walking ? 0.7F : 0.0F;
        this.sidewaysSpeed = 0.0F;
        // Hop up a step it walks into, or out of water.
        this.setJumping(walking && (this.horizontalCollision || this.isTouchingWater()));
        this.setPitch(MathHelper.clamp(this.getPitch() * 0.9F, -10F, 10F));
    }

    /** Back on its feet at spawn: what pressing Respawn does, done in place. */
    private void respawnHere() {
        ServerWorld world = this.getServer().getOverworld();
        BlockPos spawn = world.getSpawnPos();
        this.dead = false;
        this.deathTime = 0;
        this.hurtTime = 0;
        this.setHealth(this.getMaxHealth());
        this.clearStatusEffects();
        this.extinguish();
        this.fallDistance = 0;
        this.getHungerManager().setFoodLevel(20);
        this.getHungerManager().setSaturationLevel(5.0F);
        double y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        this.teleport(world, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, this.getYaw(), 0F);
        // Everyone watching still has the dying body (on its side, flushed red): make their game
        // forget it and see the dummy fresh, standing.
        net.minecraft.server.world.ServerChunkManager chunks = world.getChunkManager();
        chunks.unloadEntity(this);
        chunks.loadEntity(this);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        nextChange = 0;
    }
}
