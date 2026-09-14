# TACZ Integration Notes

This fork intentionally has **no compile-time dependency on TACZ**. Runtime integration is optional and reflection-based.

For TACZ 1.20.1 the implementation attempts to read:

- `com.tacz.guns.api.item.IGun#getGunId`
- `com.tacz.guns.api.TimelessAPI#getCommonGunIndex`
- `CommonGunIndex#getBulletData`
- `BulletData#getGravity` / `getFriction`
- `IGunOperator#getCacheProperty` and `GunProperties.AMMO_SPEED`
- `AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER`

It also detects the local player's freshly spawned `com.tacz.guns.entity.EntityKineticBullet` and uses its velocity for live per-gun calibration. Private gravity/friction fields are read only opportunistically; failure falls back safely to public TACZ data or manual settings.

No TACZ network messages are sent and no TACZ server/gameplay state is modified.
