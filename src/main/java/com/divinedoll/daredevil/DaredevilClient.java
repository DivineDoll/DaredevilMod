package com.divinedoll.daredevil;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Cliente de Daredevil: sentido radar siempre activo (shader + mobs resaltados)
 * y teclas de las habilidades.
 */
public class DaredevilClient implements ClientModInitializer {
    private static final Identifier RADAR_SHADER = new Identifier(Daredevil.MOD_ID, "shaders/post/radar.json");
    private static final Identifier RELOAD_ID = new Identifier(Daredevil.MOD_ID, "radar_reload");

    private static boolean radarEnabled = true;
    private static boolean needsApply = true;
    private static Object lastWorld = null;
    private static Entity lastCamera = null;
    private static Perspective lastPerspective = null;

    // Mobs que este cliente tiene resaltados por el radar
    private static final Set<Integer> glowing = new HashSet<>();

    private static KeyBinding toggleKey;
    private static KeyBinding throwKey;
    private static KeyBinding grappleKey;
    private static KeyBinding agilityKey;

    @Override
    public void onInitializeClient() {
        toggleKey = key("key.daredevil.toggle_radar", GLFW.GLFW_KEY_J);
        throwKey = key("key.daredevil.throw", GLFW.GLFW_KEY_G);
        grappleKey = key("key.daredevil.grapple", GLFW.GLFW_KEY_V);
        agilityKey = key("key.daredevil.agility", GLFW.GLFW_KEY_B);

        // Renderizado del bastón lanzado (usa el modelo del ítem)
        EntityRendererRegistry.register(Daredevil.BATON_ENTITY, ctx -> new FlyingItemEntityRenderer<>(ctx));

        // Tras recargar recursos (F3+T) hay que volver a cargar el shader
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        needsApply = true;
                    }
                });

        ClientTickEvents.END_CLIENT_TICK.register(DaredevilClient::onTick);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (radarEnabled && client.player != null && !client.options.hudHidden) {
                context.drawText(client.textRenderer,
                        Text.translatable("hud.daredevil.radar"),
                        6, 6, 0xFF40E0, true);
            }
        });
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(name, InputUtil.Type.KEYSYM, code, "category.daredevil"));
    }

    private static void sendAbility(int id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(id);
        ClientPlayNetworking.send(Daredevil.ABILITY_PACKET, buf);
    }

    private static void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            radarEnabled = !radarEnabled;
            if (radarEnabled) {
                needsApply = true;
            } else {
                client.gameRenderer.disablePostProcessor();
                clearGlowing(client);
            }
        }

        if (client.world == null || client.player == null) {
            lastWorld = null;
            lastCamera = null;
            lastPerspective = null;
            glowing.clear();
            needsApply = true;
            return;
        }

        // Habilidades (el servidor las ejecuta)
        while (throwKey.wasPressed()) sendAbility(Abilities.THROW);
        while (grappleKey.wasPressed()) sendAbility(Abilities.GRAPPLE);
        while (agilityKey.wasPressed()) sendAbility(Abilities.AGILITY);

        // Vanilla apaga el post-proceso al cambiar de mundo, de cámara o de perspectiva (F5)
        Perspective perspective = client.options.getPerspective();
        if (client.world != lastWorld
                || client.getCameraEntity() != lastCamera
                || perspective != lastPerspective) {
            lastWorld = client.world;
            lastCamera = client.getCameraEntity();
            lastPerspective = perspective;
            needsApply = true;
        }

        if (radarEnabled && needsApply) {
            client.gameRenderer.loadPostProcessor(RADAR_SHADER);
            needsApply = false;
        }

        if (radarEnabled) {
            updateGlowing(client);
        }
    }

    /** Resalta (a través de paredes) a los seres vivos cercanos, solo en este cliente. */
    private static void updateGlowing(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(32.0);
        Set<Integer> current = new HashSet<>();
        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && !(e instanceof ArmorStandEntity))) {
            e.setGlowing(true);
            current.add(e.getId());
        }
        // Quitar el resaltado a los que ya salieron del rango
        for (Integer id : glowing) {
            if (!current.contains(id)) {
                Entity e = client.world.getEntityById(id);
                if (e != null) e.setGlowing(false);
            }
        }
        glowing.clear();
        glowing.addAll(current);
    }

    private static void clearGlowing(MinecraftClient client) {
        if (client.world != null) {
            for (Integer id : glowing) {
                Entity e = client.world.getEntityById(id);
                if (e != null) e.setGlowing(false);
            }
        }
        glowing.clear();
    }
}
