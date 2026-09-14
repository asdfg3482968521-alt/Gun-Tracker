package com.crispywafer.crispywaferguntracker;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

/**
 * ItemForceMover —— 强制移动物品（Shift+左键的替代实现）。
 *
 * 背景（抓包实证）：
 *   这个服务端的自定义容器菜单只认 ClickType.PICKUP（普通点击），
 *   客户端发 QUICK_MOVE（Shift+左键）/ SWAP（数字键）时，服务端直接忽略、什么都不做。
 *   所以我们不能用原版的"快速移动"，只能用两次 PICKUP 自己走一遍：
 *       ① PICKUP 源槽位   -> 物品进光标
 *       ② PICKUP 目标槽位 -> 物品从光标落下
 *
 * 两个必须注意的点（都是踩过的坑）：
 *   1. carriedItem 必须填**真实的当前光标物品**（menu.getCarried()）。
 *      之前用伪造值发同一个字段，服务端因为"和它记录的状态不一致"直接静默丢弃。
 *   2. 第二条点击要等一 tick 再发 —— 服务端处理完第一条会把 stateId 推进，
 *      若第二条仍带旧 stateId，会被判为过期请求。
 */
@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT)
public final class ItemForceMover {

    /** 玩家背包在 menu.slots 里占最后 36 格（MC 约定：27 主背包 + 9 快捷栏） */
    private static final int PLAYER_INV_SIZE = 36;

    /** 等待放下的目标槽位；<0 表示空闲 */
    private static int pendingDstSlot = -1;
    /** 还差几 tick 才发第二条 */
    private static int pendingDelay = 0;

    /** 整理模式：开启后每 tick 自动推进一次"同种合并"，直到没有可合并的 */
    private static boolean autoMerge = false;

    /** 上一个 tick 探测到的"可合并对数"，用于自动停止 */
    private static int lastProbe = -1;

    public static void setAutoMerge(boolean on) {
        autoMerge = on;
        lastProbe = -1;
    }

    public static boolean isAutoMerge() {
        return autoMerge;
    }

    private ItemForceMover() {}

    // ================================================================ 对外入口
    /** 把鼠标当前悬停槽位里的物品，"快速移动"到对侧。等价于原版 Shift+左键的意图 */
    public static boolean forceMoveHovered() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        AbstractContainerScreen<?> screen = asContainerScreen(mc);
        if (screen == null) return false;

        Slot hovered = hoveredSlot(screen);
        if (hovered == null || hovered.getItem().isEmpty()) return false;

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return false;

        int src = hovered.index;
        int dst = findTargetSlot(menu, src, mc.player);
        if (dst < 0 || dst == src) return false;

        return moveSlot(menu, src, dst, mc);
    }

    /** 指定源/目标槽位的强制移动（也可给命令/UI 复用） */
    public static boolean moveSlot(AbstractContainerMenu menu, int src, int dst, Minecraft mc) {
        if (menu == null || mc.player == null || mc.getConnection() == null) return false;
        if (!sendPickup(menu, src, mc)) return false;
        // 第二条延迟 2 tick，等服务端把 stateId 推回来
        pendingDstSlot = dst;
        pendingDelay = 2;
        return true;
    }

    /**
     * 把当前容器里"所有同种可堆叠物品"合并到第一格（子弹/药品整理）。
     * 只做堆叠合并，不做位置重排 —— 因为"物品占几格"只是 UI 绘制层的信息，
     * 服务端只有扁平槽位号，重排的"整齐"在协议层没有对应概念。
     */
    public static int mergeStacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return 0;
        List<Slot> slots = menu.slots;
        int moved = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = slots.get(i).getItem();
            if (a.isEmpty() || !a.isStackable()) continue;
            if (a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                ItemStack b = slots.get(j).getItem();
                if (b.isEmpty() || !ItemStack.isSameItemSameTags(a, b)) continue;
                moveSlot(menu, j, i, mc);   // 把 j 搬到 i 上，服务端会自动合并
                moved++;
                break;                       // 一个 tick 只搬一次，避免 stateId 打架
            }
            if (moved > 0) break;
        }
        return moved;
    }

    /** 由 tick 事件驱动：发送延迟的第二条点击 */
    public static void tick() {
        if (pendingDstSlot < 0) return;
        if (--pendingDelay > 0) return;

        Minecraft mc = Minecraft.getInstance();
        int dst = pendingDstSlot;
        pendingDstSlot = -1;
        if (mc.player == null || mc.getConnection() == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return;
        sendPickup(menu, dst, mc);
    }

    public static boolean isPending() {
        return pendingDstSlot >= 0;
    }

    // ================================================================ 事件驱动
    /**
     * 独立的事件处理器 —— 刻意不放进 AimHandler：
     * AimHandler.onClientTick 在"打开了任何界面"时会提前 return，
     * 而移动物品恰恰只在容器界面打开时才有意义，放那里永远不会执行。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tick();   // 发送延迟的第二条点击

        // 整理模式：等前一次搬运收尾后，再推进下一步；没有可合并的就自动停
        if (autoMerge && !isPending()) {
            int n = mergeStacks();
            if (n == 0) {
                autoMerge = false;
            }
        }

        KeyMapping key = Keybindings.forceMoveKey;
        if (key == null) return;
        while (key.consumeClick()) {
            forceMoveHovered();
        }

        KeyMapping mk = Keybindings.mergeKey;
        if (mk != null) {
            while (mk.consumeClick()) {
                setAutoMerge(true);   // 一键开始整理；搬完自动停
            }
        }
    }

    // ================================================================ 目标槽位计算
    /**
     * 复刻原版 quickMoveStack 的意图：
     *   · 源在玩家背包 -> 目标找容器侧第一个空槽（没有空槽就找可堆叠的同类）
     *   · 源在容器侧   -> 目标找玩家背包侧第一个空槽
     */
    private static int findTargetSlot(AbstractContainerMenu menu, int src, Player player) {
        List<Slot> slots = menu.slots;
        int n = slots.size();
        if (n <= 0) return -1;
        int invStart = Math.max(0, n - PLAYER_INV_SIZE);
        boolean fromPlayer = src >= invStart;

        ItemStack srcStack = slots.get(src).getItem();

        if (fromPlayer) {
            // 背包 -> 容器：先找空槽，再找可堆叠的同类
            for (int i = 0; i < invStart; i++) {
                if (!slots.get(i).mayPlace(srcStack)) continue;
                if (slots.get(i).getItem().isEmpty()) return i;
            }
            for (int i = 0; i < invStart; i++) {
                if (!slots.get(i).mayPlace(srcStack)) continue;
                ItemStack s = slots.get(i).getItem();
                if (ItemStack.isSameItemSameTags(s, srcStack) && s.getCount() < s.getMaxStackSize()) return i;
            }
        } else {
            // 容器 -> 背包：先找空槽，再找可堆叠的同类（优先快捷栏）
            for (int i = invStart; i < n; i++) {
                if (!slots.get(i).mayPlace(srcStack)) continue;
                if (slots.get(i).getItem().isEmpty()) return i;
            }
            for (int i = invStart; i < n; i++) {
                if (!slots.get(i).mayPlace(srcStack)) continue;
                ItemStack s = slots.get(i).getItem();
                if (ItemStack.isSameItemSameTags(s, srcStack) && s.getCount() < s.getMaxStackSize()) return i;
            }
        }
        return -1;
    }

    // ================================================================ 发包
    private static boolean sendPickup(AbstractContainerMenu menu, int slot, Minecraft mc) {
        try {
            int containerId = menu.containerId;
            int stateId = menu.getStateId();
            ItemStack carried = menu.getCarried();          // ← 真实光标物品，不能伪造
            ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
                    containerId, stateId, slot, 0, ClickType.PICKUP,
                    carried, new Int2ObjectOpenHashMap<>()
            );
            mc.getConnection().send(packet);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ================================================================ 悬停槽位
    private static AbstractContainerScreen<?> asContainerScreen(Minecraft mc) {
        return (mc.screen instanceof AbstractContainerScreen<?> cs) ? cs : null;
    }

    /**
     * 取鼠标悬停的槽位。
     * 注意：AbstractContainerScreen.hoveredSlot 是 protected，且生产环境字段名会被混淆，
     *      所以必须走 Forge 的 ObfuscationReflectionHelper（它负责 mojmap <-> SRG 映射），
     *      直接 getDeclaredField("hoveredSlot") 在正式版会失败。
     */
    /** hoveredSlot 的候选名：mojmap 名 + SRG 名（正式版运行时是后者，javap 已确认 f_97734_） */
    private static final String[] HOVERED_NAMES = { "hoveredSlot", "f_97734_" };

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        for (String name : HOVERED_NAMES) {
            try {
                Slot s = ObfuscationReflectionHelper.getPrivateValue(
                        AbstractContainerScreen.class, screen, name);
                if (s != null) return s;
            } catch (Throwable ignore) { }
        }
        // 最后退化：按字段类型找（顺序不定，仅在前面都失败时兜底）
        for (Class<?> c = AbstractContainerScreen.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != Slot.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(screen);
                    if (v instanceof Slot s) return s;
                } catch (Throwable ignore) { }
            }
        }
        return null;
    }
}
