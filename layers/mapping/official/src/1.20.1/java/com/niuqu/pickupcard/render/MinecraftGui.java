package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.scores.Score;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.function.Supplier;

final class MinecraftGui {
    private static final Matrix4f FLIP_Y = new Matrix4f().scaling(1f, -1f, 1f);

    private MinecraftGui() {}

    record Sidebar(int width, int lines) {}

    static Sidebar sidebar(Minecraft mc) {
        var objective = mc.level.getScoreboard().getDisplayObjective(1);
        if (objective == null) {
            return null;
        }
        int widest = mc.font.width(objective.getDisplayName());
        int lines = 0;
        for (Score score : objective.getScoreboard().getPlayerScores(objective)) {
            if (++lines > 15) {
                break;
            }
            String owner = score.getOwner();
            if (owner != null && !owner.startsWith("#")) {
                widest = Math.max(widest, mc.font.width(owner) + 8 + mc.font.width(Integer.toString(score.getScore())));
            }
        }
        return new Sidebar(widest, Math.max(1, Math.min(lines, 15)));
    }

    static boolean isBeneficial(MobEffectInstance effect) {
        return effect.getEffect().isBeneficial();
    }

    static void addFadingLayers(Map<RenderType, RenderType> layers, Supplier<RenderType> mippedTranslucent) {}

    static void flipAndScaleItem(PoseStack pose, float scale) {
        pose.mulPoseMatrix(FLIP_Y);
        pose.scale(scale, scale, scale);
    }
}
