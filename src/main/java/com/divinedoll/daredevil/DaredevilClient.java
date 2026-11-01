package com.divinedoll.daredevil;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Sentido radar de Daredevil: aplica siempre el shader de contornos,
 * lleves el traje o no y tengas poderes activos o no.
 */
public class DaredevilClient implements ClientModInitializer {
    public static final String MOD_ID = "daredevil";
    private static final Identifier RADAR_SHADER = new Identifier(MOD_ID, "shaders/post/radar.json");
    private static final Identifier RELOAD_ID = new Identifier(MOD_ID, "radar_reload");

    private static boolean radarEnabled = true;
    private static boolean needsApply = true;
    private static Object lastWorld = null;
    private static Entity lastCamera = null;

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.daredevil.toggle_radar",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.daredevil"
        ));

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

    private static void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            radarEnabled = !radarEnabled;
            if (radarEnabled) {
                needsApply = true;
            } else {
                client.gameRenderer.disablePostProcessor();
            }
        }

        if (client.world == null || client.player == null) {
            lastWorld = null;
            lastCamera = null;
            needsApply = true;
            return;
        }

        // Entrar a un mundo, cambiar de dimensión o de entidad de cámara
        // (vanilla desactiva el post-proceso en esos casos)
        if (client.world != lastWorld || client.getCameraEntity() != lastCamera) {
            lastWorld = client.world;
            lastCamera = client.getCameraEntity();
            needsApply = true;
        }

        if (radarEnabled && needsApply) {
            client.gameRenderer.loadPostProcessor(RADAR_SHADER);
            needsApply = false;
        }
    }
}
