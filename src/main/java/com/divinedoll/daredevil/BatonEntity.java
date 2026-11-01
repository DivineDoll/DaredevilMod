package com.divinedoll.daredevil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Bastón lanzado: va recto, golpea y vuelve hacia quien lo lanzó. */
public class BatonEntity extends ThrownItemEntity {
    private static final float DAMAGE = 7.0f;
    private static final int OUTBOUND_TICKS = 14;
    private static final int MAX_AGE = 120;

    private int age = 0;
    private boolean returning = false;

    public BatonEntity(EntityType<? extends BatonEntity> type, World world) {
        super(type, world);
    }

    public BatonEntity(World world, LivingEntity owner) {
        super(Daredevil.BATON_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Daredevil.BATON;
    }

    @Override
    protected float getGravity() {
        return 0.0f;
    }

    private void startReturn() {
        returning = true;
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        age++;
        if (!returning && age > OUTBOUND_TICKS) startReturn();

        if (returning) {
            Entity owner = getOwner();
            if (owner == null || !owner.isAlive() || age > MAX_AGE) {
                discard();
                return;
            }
            Vec3d toOwner = owner.getEyePos().subtract(getPos());
            if (toOwner.length() < 1.5) {
                discard();
                return;
            }
            setVelocity(toOwner.normalize().multiply(1.3));
            velocityDirty = true;
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (returning) return;
        Entity target = hit.getEntity();
        target.damage(getDamageSources().thrown(this, getOwner()), DAMAGE);
        startReturn();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (returning) return;
        startReturn();
    }
}
