package com.divinedoll.daredevil;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Lado común: ítem del bastón, entidad lanzada y habilidades (en el servidor).
 */
public class Daredevil implements ModInitializer {
    public static final String MOD_ID = "daredevil";
    public static final Identifier ABILITY_PACKET = new Identifier(MOD_ID, "ability");

    public static final Item BATON = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "baton"),
            new Item(new FabricItemSettings().maxCount(1)));

    public static final EntityType<BatonEntity> BATON_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "baton"),
            FabricEntityTypeBuilder.<BatonEntity>create(SpawnGroup.MISC, BatonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(BATON));

        ServerPlayNetworking.registerGlobalReceiver(ABILITY_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    int id = buf.readVarInt();
                    server.execute(() -> Abilities.handle(player, id));
                });

        ServerTickEvents.END_SERVER_TICK.register(Abilities::tick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                Abilities.clear(handler.player.getUuid()));

        // Sin daño de caída con agilidad o mientras te balanceas
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof PlayerEntity player
                    && source.isOf(DamageTypes.FALL)
                    && player instanceof ServerPlayerEntity sp
                    && Abilities.noFall(sp)) {
                return false;
            }
            return true;
        });
    }
}
