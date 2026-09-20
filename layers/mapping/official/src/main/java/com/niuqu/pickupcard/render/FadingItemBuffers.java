package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.style.Easing;import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品图标的<b>换层淡出</b>直画器：图标永远每帧走原版 {@code ItemRenderer.render} 现渲
 * （附魔光动画、物理分辨率、原版亮度、原版方向都是本尊），只在退场 {@code alpha < 1} 的
 * 那几帧，把物品落到的<b>无混合</b>实体渲染层换到开混合的等价层提交 ——
 * {@code RenderSystem.setShaderColor(1,1,1,alpha)} 从此数学上有效，三档退场（FADE/TRAIN/WIPE）
 * 都是真 alpha 淡出。
 *
 * <p>【为什么它存在（2026-09-19 治本定案，替代已删除的 ItemIconCache 烘焙路线）】
 * 方块与大量 mod 物品经 Forge {@code ItemBlockRenderTypes} 落在 entitySolid/entityCutout
 * 渲染层 —— 这两层<b>不开混合</b>：帧缓冲不拿 fragment 的 alpha 去混合，setShaderColor 的
 * alpha 分量对它们无效，直画路线上图标数学上不可能淡出（只能满亮到摘卡，或调 RGB 变黑）。
 * 上一版为了绕开它把图标烘成离屏贴图：淡出是真了，但快照冻住了活的东西 —— 附魔光不再
 * 滚动、分辨率钉死在贴图、glint 亮条纹烘丢（变暗）、读回行翻转账（颠倒）。根治不是把快照
 * 修好，是<b>不再快照</b>：淡出的病根是"混合没开"，那就只在淡出的那几帧把层换到开混合的。
 *
 * <p>【目标层为什么安全】平贴图物品从第一天起就走 entityTranslucentCull（开混合），
 * 它们的淡出一直是对的 —— 换层就是让实心物品退场时也走这一类层。方块模型有背面剔除 +
 * 深度写入，每个像素只画一次，混合不会叠出伪影。
 *
 * <p>【查不到的类型直通】映射只覆盖物品会落到的已知无混合层（solid / cutout / cutoutNoCull，
 * 静态构造一次）；mod 自造层查不到就原样提交 —— 后果只是"这种物品退场不淡出"，不会错画。
 */
public final class FadingItemBuffers implements MultiBufferSource {

    private static final ResourceLocation ATLAS = InventoryMenu.BLOCK_ATLAS;
    private static final FadingItemBuffers INSTANCE = new FadingItemBuffers();

    /** 图标的 y 翻转矩阵（原版 renderItem 同款）。mulPoseMatrix 只读它，静态一份免每帧分配。 */
    private static final Matrix4f FLIP_Y = new Matrix4f().scaling(1.0F, -1.0F, 1.0F);

    /** 无混合实体层 -> 开混合等价层。键按结构相等构造（与 ItemBlockRenderTypes 同一构造调用）。 */
    private static final Map<RenderType, RenderType> BLENDED = new HashMap<>();

    static {
        // 实心方块模型：entityTranslucent 带光照与覆盖层，剔除状态同为开
        BLENDED.put(RenderType.entitySolid(ATLAS), RenderType.entityTranslucent(ATLAS));
        // 剪裁层（有透明像素的贴图）：目标层就是平贴图物品一直用的那一个
        BLENDED.put(RenderType.entityCutout(ATLAS), RenderType.entityTranslucentCull(ATLAS));
        // 无剔除剪裁层（交叉面片，草/花类）：没有现成目标，静态自造一个（见 Shards）
        BLENDED.put(RenderType.entityCutoutNoCull(ATLAS), translucentNoCull(ATLAS));
    }

    private FadingItemBuffers() {
    }

    /**
     * 在当前 pose / 裁剪下画一个物品图标：步骤逐字对齐 1.20.1 {@code GuiGraphics.renderItem}
     * （translate 中心 → y 翻转矩阵 → scale(16) → render → flush → 光照恢复），只多两件事 ——
     * 缩放比（{@code iconSize / 16}）和 bufferSource 可换成换层装饰器。
     *
     * @param centerX centerY 图标中心的<b>卡内未缩放坐标</b>（调用方已把 pose 变换到卡空间）
     * @param iconSize 目标边长（逻辑 px）；原版图标 16
     * @param alpha 这张卡本帧的不透明度；=1 走普通直画，&lt;1 走换层淡出
     * @param clipped 调用方此刻是否开着入场/退场裁剪。开着就必须先清队列（见下），稳态不用
     */
    public static void drawIcon(GuiGraphics gui, ItemStack stack, float centerX, float centerY,
                                float iconSize, float alpha, boolean clipped) {
        if (stack.isEmpty()) {
            return;
        }
        // 同「末帧闪」的尺子（见 NvgCardPainter#textVisible）：alpha 字节 <4 连画都不画 ——
        // 画了也会全亮一帧，宁可图标比外壳早没半帧。
        if (Math.round(Easing.clamp01(alpha) * 255f) < 4) {
            return;
        }
        boolean fading = alpha < 0.999f;
        Minecraft mc = Minecraft.getInstance();
        // 【先清队列】共享批次里还排着前几张卡的文字，它们会在图标的 flush 里一起上屏 ——
        // 而此刻的 scissor/全局色是这张卡的，不清队列就会错裁、错淡别人的内容。
        // 【清队列是有代价的】整条批次提交一次 GPU；稳态（没淡出、没裁剪）无污染可言，
        // 免掉这一次 flush —— 一摞卡就是一帧省 N 次提交（2026-09-20 性能轮）。
        if (fading || clipped) {
            BatchStats.countFlush();
            gui.flush();
        }
        if (fading) {
            RenderSystem.setShaderColor(1f, 1f, 1f, Easing.clamp01(alpha));
        }
        try {
            ItemRenderer renderer = mc.getItemRenderer();
            BakedModel model = renderer.getModel(stack, mc.level, null, 0);
            PoseStack pose = gui.pose();
            pose.pushPose();
            // —— 从这里起与 GuiGraphics.renderItem 逐字对齐 ——
            pose.translate(centerX, centerY, 150f);
            // y 翻转矩阵只读不存（mulPoseMatrix 把值乘进 pose 栈），共享一份免每帧分配
            pose.mulPoseMatrix(FLIP_Y);
            float scale = iconSize / CardMetrics.ICON_PX;
            pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
            boolean flatLight = !model.usesBlockLight();
            if (flatLight) {
                Lighting.setupForFlatItems();
            }
            renderer.render(stack, ItemDisplayContext.GUI, false, pose,
                    fading ? INSTANCE : mc.renderBuffers().bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
            // 原版 renderItem 在恢复光照前 flush（uniform 在 draw 时才吃）—— 同序。
            BatchStats.countFlush();
            mc.renderBuffers().bufferSource().endBatch();
            if (flatLight) {
                Lighting.setupFor3DItems();
            }
        } catch (Exception e) {
            PickupCard.LOGGER.error("[图标] 直画失败（{}）—— 这张卡图标该帧缺失",
                    stack.getItem(), e);
        } finally {
            if (fading) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
    }

    /** 换层装饰：查表命中就换到开混合的等价层，否则原层直通。批次仍进共享 BufferSource。 */
    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(BLENDED.getOrDefault(type, type));
    }

    /**
     * 自造的"无剔除 + 开混合"实体层：交叉面片物品（草/花类）退场时的目标层。
     * 原版没有现成等价物（entityTranslucent 系都开剔除，会把交叉面片的背面整片剔没）。
     * 状态逐项对齐 entityTranslucent，只把剔除换成 NO_CULL。
     */
    private static RenderType translucentNoCull(ResourceLocation atlas) {
        return RenderType.create("pickupcard_entity_translucent_no_cull",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(Shards.ENTITY_TRANSLUCENT)
                        .setTextureState(Shards.texture(atlas))
                        .setTransparencyState(Shards.TRANSLUCENT)
                        .setCullState(Shards.NO_CULLING)
                        .setLightmapState(Shards.LIGHTMAP_SHARD)
                        .setOverlayState(Shards.OVERLAY_SHARD)
                        .createCompositeState(false));
    }

    /**
     * protected 渲染状态常量的访问桥：Java 允许子类引用父类的 protected 静态成员与
     * protected 构造。这个类永不实例化，只为拿到 {@link RenderStateShard} 里那几个 shard。
     */
    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard ENTITY_TRANSLUCENT = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULLING = NO_CULL;
        static final LightmapStateShard LIGHTMAP_SHARD = LIGHTMAP;
        static final OverlayStateShard OVERLAY_SHARD = OVERLAY;

        static TextureStateShard texture(ResourceLocation atlas) {
            return new TextureStateShard(atlas, false, false);
        }

        private Shards() {
            super("", () -> { }, () -> { });
        }
    }
}
