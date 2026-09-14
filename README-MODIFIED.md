# CrispyWafer Gun Tracker - Singleplayer Enhanced v1.3 TACZ

基于 Sodium_CrispyWafer 的 Gun Tracker Mod（MIT）修改，目标环境为 **Minecraft Forge 1.20.1 + TACZ**，只用于本地单人世界的枪械测试与 PvE。

## 硬限制

- 自瞄逻辑仅在 Integrated Singleplayer Server 存在时运行。
- LAN 世界出现第二名玩家后，锁定、Flick、TACZ 弹道读取与预测全部立即清空/停止。
- 不包含反作弊绕过、数据包伪装、注入、服务器端命中修改或自动开火。

## v1.3 TACZ 强化

- **TACZ 自动弹道识别**：可选反射读取当前主手 TACZ 枪械的 gunId、AMMO_SPEED、BulletData gravity/friction 和全局弹速倍率；TACZ 不存在时自动退回手动配置，不构成硬依赖。
- **实弹实时校准**：捕获本地玩家刚发射出的 `EntityKineticBullet`，按 gunId 缓存真实初速；若能读取到子弹实例上的 gravity/friction，则一并更新。适合配件、枪包或脚本改变弹速的情况。
- **TACZ 离散轨迹模型**：按 TACZ 的实际 tick 顺序计算——先移动，再乘 `(1 - friction)`，再施加 gravity，而不是简单使用 `0.5*g*t^2`。
- **迭代截获解算**：对移动目标寻找最早可达截获时间，同时计算阻力、重力、目标速度、目标加速度和玩家自身移动继承速度。
- **目标运动滤波**：位置速度与 `getDeltaMovement()` 混合，再用 EWMA 平滑速度/加速度；对瞬移、台阶、击退尖峰做速度/加速度限幅。
- **加速度预测**：使用经过滤波且衰减后的目标加速度，让横移、跳跃、突然变向比 v1.2 的恒速外推更稳。
- **性能优化**：有锁定目标时默认每 2 tick 才进行一次完整候选扫描；无目标时仍每 tick 搜索。FOV 检查在 LOS raytrace 之前执行。
- **O(1) 重力累计**：TACZ 离散重力/阻力累计使用闭式公式，预测窗口变长时不会按 tick 数线性增加单次求值成本。
- **弹道 HUD**：显示当前来源（TACZ 数据 / TACZ 实弹校准 / 手动）、弹速、gravity、friction 和解算出的 TOF。

## 默认暴力参数

- 持续自瞄瞬吸：开
- 瞄准点：头部 / 眼睛
- FOV 中心夹角：70°
- 最大搜索距离：96 格
- 移动预测：开
- 目标加速度预测：开
- TACZ 自动弹道：开
- TACZ 实弹校准：开
- 最大预测时间：30 tick
- 粘性锁定与切换迟滞：开
- 锁定后完整重扫：每 2 tick
- 仅锁可见目标：开

## TACZ 弹道来源优先级

1. **TACZ 实弹校准**：最近实际发射的同 gunId 子弹，优先级最高。
2. **TACZ 数据读取**：从 TACZ 当前枪械/配件缓存和 BulletData 获取近似实际参数。
3. **手动参数**：TACZ 未安装、不是 TACZ 枪或反射接口变化时使用。

实弹校准缓存会较快过期，目的是在你换配件后尽快重新读取当前属性；重新开火即可刷新。

## 配置建议

高初速步枪/狙击枪通常让自动 TACZ 参数接管即可。榴弹、特殊脚本枪、非 `EntityKineticBullet` 自定义投射物可能需要关闭 `自动读取 TACZ 弹道` 并手动设置速度、阻力和重力。

如果目标运动非常随机，可降低“目标加速度滤波”或直接关闭“预测目标加速度”；如果目标稳定横移，保持开启通常更准。

## 构建

见 `BUILDING.md`。项目目标 Java 17、Minecraft 1.20.1、Forge 47.3.22。TACZ 为可选运行时兼容项，因此源码本身不需要 TACZ 作为编译依赖。
