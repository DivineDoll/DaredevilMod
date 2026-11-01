package com.divinedoll.daredevil;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Habilidades de Daredevil, ejecutadas en el servidor. */
public final class Abilities {
    public static final int THROW = 0;
    public static final int GRAPPLE = 1;
    public static final int AGILITY = 2;

    private static final int THROW_COOLDOWN_TICKS = 20;
    private static final double GRAPPLE_RANGE = 40.0;
    private static final int GRAPPLE_MAX_TICKS = 100;

    private static final Set<UUID> agility = new HashSet<>();
    private static final Map<UUID, Anchor> anchors = new HashMap<>();
    private static final Map<UUID, Integer> lastThrow = new HashMap<>();

    private static class Anchor {
        final Vec3d pos;
        int ticksLeft = GRAPPLE_MAX_TICKS;

        Anchor(Vec3d pos) {
            this.pos = pos;
        }
    }

    private Abilities() {
    }

    public static void handle(ServerPlayerEntity player, int id) {
        if (!player.isAlive() || player.isSpectator()) return;
        switch (id) {
            case THROW -> throwBaton(player);
            case GRAPPLE -> toggleGrapple(player);
            case AGILITY -> toggleAgility(player);
            default -> {
            }
        }
    }

    public static boolean noFall(ServerPlayerEntity player) {
        return agility.contains(player.getUuid()) || anchors.containsKey(player.getUuid());
    }

    public static void clear(UUID uuid) {
        agility.remove(uuid);
        anchors.remove(uuid);
        lastThrow.remove(uuid);
    }

    // ---------- Bastón lanzado (vuelve como un boomerang) ----------

    private static void throwBaton(ServerPlayerEntity player) {
        int now = player.getServer().getTicks();
        Integer last = lastThrow.get(player.getUuid());
        if (last != null && now - last < THROW_COOLDOWN_TICKS) return;
        lastThrow.put(player.getUuid(), now);

        BatonEntity baton = new BatonEntity(player.getWorld(), player);
        baton.setPosition(player.getEyePos().add(0, -0.2, 0));
        baton.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 1.8f, 0.0f);
        player.getWorld().spawnEntity(baton);
    }

    // ---------- Cuerda: te impulsa hacia el punto donde apuntas ----------

    private static void toggleGrapple(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (anchors.remove(id) != null) {
            player.sendMessage(Text.translatable("msg.daredevil.grapple_off"), true);
            return;
        }
        HitResult hit = player.raycast(GRAPPLE_RANGE, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            anchors.put(id, new Anchor(hit.getPos()));
            player.sendMessage(Text.translatable("msg.daredevil.grapple_on"), true);
        } else {
            player.sendMessage(Text.translatable("msg.daredevil.grapple_miss"), true);
        }
    }

    // ---------- Agilidad: velocidad, salto, fuerza y sin daño de caída ----------

    private static void toggleAgility(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (agility.remove(id)) {
            player.sendMessage(Text.translatable("msg.daredevil.agility_off"), true);
        } else {
            agility.add(id);
            applyAgility(player);
            player.sendMessage(Text.translatable("msg.daredevil.agility_on"), true);
        }
    }

    private static void applyAgility(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, false, false, false));
    }

    // ---------- Tick del servidor ----------

    public static void tick(MinecraftServer server) {
        // Refrescar los efectos de agilidad
        if (server.getTicks() % 20 == 0) {
            for (UUID id : agility) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null && p.isAlive()) applyAgility(p);
            }
        }

        // Mover a quienes están usando la cuerda
        Iterator<Map.Entry<UUID, Anchor>> it = anchors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Anchor> entry = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            Anchor a = entry.getValue();
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }

            Vec3d body = p.getPos().add(0, p.getHeight() * 0.5, 0);
            Vec3d toTarget = a.pos.subtract(body);
            double dist = toTarget.length();

            if (dist < 2.0 || --a.ticksLeft <= 0) {
                it.remove();
                p.sendMessage(Text.translatable("msg.daredevil.grapple_off"), true);
                continue;
            }

            Vec3d v = p.getVelocity().multiply(0.97).add(toTarget.normalize().multiply(0.22));
            if (v.length() > 1.5) v = v.normalize().multiply(1.5);
            p.setVelocity(v);
            p.velocityModified = true;
            p.fallDistance = 0.0f;

            // Cuerda visible con partículas, desde la mano hasta el punto de anclaje
            if (p.getWorld() instanceof ServerWorld world) {
                Vec3d start = p.getEyePos().add(0, -0.3, 0);
                Vec3d line = a.pos.subtract(start);
                int steps = 8;
                for (int i = 1; i <= steps; i++) {
                    Vec3d pt = start.add(line.multiply(i / (double) steps));
                    world.spawnParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
