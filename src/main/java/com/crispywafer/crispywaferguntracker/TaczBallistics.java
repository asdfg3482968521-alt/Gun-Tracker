package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional TACZ integration without a hard compile dependency. It reads public TACZ
 * APIs through reflection and opportunistically calibrates from actual TACZ bullets.
 * If TACZ changes an internal field, the code simply falls back to public data or the
 * manual profile instead of crashing the game.
 */
final class TaczBallistics {
    static final TaczBallistics INSTANCE = new TaczBallistics();

    private static final String TACZ_BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";
    private static final int LIVE_PROFILE_TTL = 100;

    private final Map<String, BallisticProfile> liveProfiles = new HashMap<>();
    private final ArrayDeque<Integer> capturedBulletIds = new ArrayDeque<>();
    private final Map<Integer, Boolean> capturedLookup = new HashMap<>();

    private boolean reflectionInitialized;
    private boolean reflectionAvailable;
    @Nullable private Method getIGunOrNull;
    @Nullable private Method timelessGetCommonGunIndex;
    @Nullable private Method gunOperatorFromLiving;
    @Nullable private Object ammoSpeedProperty;
    @Nullable private Object globalBulletSpeedModifier;

    private String lastHeldKey = "manual";
    private int lastResolveTick = Integer.MIN_VALUE;
    private BallisticProfile lastResolved;

    private TaczBallistics() {}

    void tick(LocalPlayer player) {
        if (!Config.TACZ_AUTO_BALLISTICS.get() || !ModList.get().isLoaded("tacz")) return;
        String heldKey = getHeldGunKey(player);
        if (heldKey == null) return;
        captureFreshBullets(player, heldKey);
    }

    BallisticProfile resolve(LocalPlayer player) {
        BallisticProfile manual = manualProfile(player.tickCount);
        if (!Config.TACZ_AUTO_BALLISTICS.get() || !ModList.get().isLoaded("tacz")) return manual;

        String heldKey = getHeldGunKey(player);
        if (heldKey == null) return manual;

        BallisticProfile live = liveProfiles.get(heldKey);
        if (live != null && player.tickCount - live.calibratedTick() <= LIVE_PROFILE_TTL) {
            lastHeldKey = heldKey;
            lastResolved = live;
            lastResolveTick = player.tickCount;
            return live;
        }

        if (lastResolved != null && heldKey.equals(lastHeldKey) && player.tickCount - lastResolveTick < 10) {
            return lastResolved;
        }

        BallisticProfile reflected = resolveFromTacZData(player, heldKey, manual);
        lastHeldKey = heldKey;
        lastResolved = reflected;
        lastResolveTick = player.tickCount;
        return reflected;
    }

    private BallisticProfile manualProfile(int tick) {
        return new BallisticProfile(
                "manual",
                Config.PROJECTILE_SPEED.get(),
                Config.PROJECTILE_FRICTION.get(),
                Config.GRAVITY_COMPENSATION.get() ? Config.PROJECTILE_GRAVITY.get() : 0.0D,
                BallisticProfile.Source.MANUAL,
                tick
        );
    }

    @Nullable
    private String getHeldGunKey(LocalPlayer player) {
        ensureReflection();
        if (!reflectionAvailable || getIGunOrNull == null) return null;
        try {
            ItemStack stack = player.getMainHandItem();
            Object iGun = getIGunOrNull.invoke(null, stack);
            if (iGun == null) return null;
            Method getGunId = iGun.getClass().getMethod("getGunId", ItemStack.class);
            Object gunId = getGunId.invoke(iGun, stack);
            return gunId == null ? null : gunId.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private BallisticProfile resolveFromTacZData(LocalPlayer player, String heldKey, BallisticProfile fallback) {
        ensureReflection();
        if (!reflectionAvailable || timelessGetCommonGunIndex == null) return fallback;
        try {
            Object resourceLocation = createResourceLocation(heldKey);
            if (resourceLocation == null) return fallback;
            Object optionalObject = timelessGetCommonGunIndex.invoke(null, resourceLocation);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) return fallback;
            Object index = optional.get();
            Object bulletData = index.getClass().getMethod("getBulletData").invoke(index);

            double gravity = number(invokeNoArg(bulletData, "getGravity"), fallback.gravityPerTick());
            double friction = number(invokeNoArg(bulletData, "getFriction"), fallback.frictionPerTick());
            double speed = resolveCachedAmmoSpeed(player, fallback.speedBlocksPerTick());

            BallisticProfile profile = new BallisticProfile(
                    heldKey,
                    sanitizeSpeed(speed, fallback.speedBlocksPerTick()),
                    sanitizeFriction(friction, fallback.frictionPerTick()),
                    sanitizeNonNegative(gravity, fallback.gravityPerTick()),
                    BallisticProfile.Source.TACZ_DATA,
                    player.tickCount
            );
            return profile.usable() ? profile : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private double resolveCachedAmmoSpeed(LocalPlayer player, double fallbackSpeed) throws ReflectiveOperationException {
        if (gunOperatorFromLiving == null || ammoSpeedProperty == null || globalBulletSpeedModifier == null) {
            return fallbackSpeed;
        }
        Object operator = gunOperatorFromLiving.invoke(null, player);
        Object cache = operator.getClass().getMethod("getCacheProperty").invoke(operator);
        if (cache == null) return fallbackSpeed;

        Method getCache = null;
        for (Method method : cache.getClass().getMethods()) {
            if (method.getName().equals("getCache") && method.getParameterCount() == 1) {
                getCache = method;
                break;
            }
        }
        if (getCache == null) return fallbackSpeed;
        Object rawSpeed = getCache.invoke(cache, ammoSpeedProperty);
        double ammoSpeed = number(rawSpeed, fallbackSpeed * 20.0D);
        double globalMultiplier = number(invokeNoArg(globalBulletSpeedModifier, "get"), 2.0D);
        return ammoSpeed * globalMultiplier / 20.0D;
    }

    private void captureFreshBullets(LocalPlayer player, String heldKey) {
        if (!Config.TACZ_LIVE_CALIBRATION.get()) return;
        List<Projectile> projectiles = player.level().getEntitiesOfClass(
                Projectile.class,
                player.getBoundingBox().inflate(48.0D)
        );
        for (Projectile projectile : projectiles) {
            if (!projectile.getClass().getName().equals(TACZ_BULLET_CLASS)) continue;
            if (projectile.getOwner() != player) continue;
            if (projectile.tickCount > 3 || capturedLookup.containsKey(projectile.getId())) continue;

            markCaptured(projectile.getId());
            String gunKey = invokeGunId(projectile);
            if (gunKey == null) gunKey = heldKey;

            BallisticProfile base = liveProfiles.get(gunKey);
            if (base == null) base = resolveFromTacZData(player, gunKey, manualProfile(player.tickCount));

            double gravity = readFloatField(projectile, "gravity", base.gravityPerTick());
            double friction = readFloatField(projectile, "friction", base.frictionPerTick());
            double speed = estimateLaunchSpeed(player, projectile, friction, gravity);
            if (!Double.isFinite(speed) || speed <= 0.05D) continue;

            liveProfiles.put(gunKey, new BallisticProfile(
                    gunKey,
                    speed,
                    sanitizeFriction(friction, base.frictionPerTick()),
                    sanitizeNonNegative(gravity, base.gravityPerTick()),
                    BallisticProfile.Source.TACZ_LIVE,
                    player.tickCount
            ));
        }
    }

    private double estimateLaunchSpeed(LocalPlayer player, Projectile projectile, double friction, double gravity) {
        Vec3 velocity = projectile.getDeltaMovement();
        double retention = Math.max(1.0e-4D, 1.0D - sanitizeFriction(friction, 0.0D));
        int age = Math.max(0, Math.min(3, projectile.tickCount));
        for (int i = 0; i < age; i++) {
            velocity = new Vec3(
                    velocity.x / retention,
                    (velocity.y + gravity) / retention,
                    velocity.z / retention
            );
        }

        Vec3 inherited = player.getDeltaMovement();
        double inheritedY = player.onGround() ? 0.0D : inherited.y;
        Vec3 launchOnly = velocity.subtract(new Vec3(inherited.x, inheritedY, inherited.z));
        return launchOnly.length();
    }

    @Nullable
    private String invokeGunId(Projectile projectile) {
        try {
            Object value = projectile.getClass().getMethod("getGunId").invoke(projectile);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private double readFloatField(Object instance, String name, double fallback) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(instance);
            return number(value, fallback);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private void markCaptured(int entityId) {
        capturedLookup.put(entityId, Boolean.TRUE);
        capturedBulletIds.addLast(entityId);
        while (capturedBulletIds.size() > 64) {
            Integer expired = capturedBulletIds.removeFirst();
            capturedLookup.remove(expired);
        }
    }

    private void ensureReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);

            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Class<?> timelessApi = Class.forName("com.tacz.guns.api.TimelessAPI");
            timelessGetCommonGunIndex = timelessApi.getMethod("getCommonGunIndex", resourceLocationClass);

            Class<?> gunOperator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            gunOperatorFromLiving = gunOperator.getMethod("fromLivingEntity", livingEntityClass);

            Class<?> gunProperties = Class.forName("com.tacz.guns.api.GunProperties");
            ammoSpeedProperty = gunProperties.getField("AMMO_SPEED").get(null);

            Class<?> ammoConfig = Class.forName("com.tacz.guns.config.common.AmmoConfig");
            globalBulletSpeedModifier = ammoConfig.getField("GLOBAL_BULLET_SPEED_MODIFIER").get(null);
            reflectionAvailable = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            reflectionAvailable = false;
        }
    }

    @Nullable
    private Object createResourceLocation(String key) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.resources.ResourceLocation");
            try {
                return clazz.getConstructor(String.class).newInstance(key);
            } catch (NoSuchMethodException ignored) {
                Method tryParse = clazz.getMethod("tryParse", String.class);
                return tryParse.invoke(null, key);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object instance, String method) throws ReflectiveOperationException {
        return instance.getClass().getMethod(method).invoke(instance);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static double sanitizeSpeed(double value, double fallback) {
        return Double.isFinite(value) && value > 0.01D ? value : fallback;
    }

    private static double sanitizeFriction(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D && value < 1.0D ? value : fallback;
    }

    private static double sanitizeNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }
}
