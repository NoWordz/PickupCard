package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import net.neoforged.neoforge.client.NeoForgeRenderTypes;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;

final class MinecraftGui {
    private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER =
            Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private MinecraftGui() {}

    record Sidebar(int width, int lines) {}

    static Sidebar sidebar(Minecraft mc) {
        var scoreboard = mc.level.getScoreboard();
        var team = mc.player == null ? null : scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        var teamSlot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
        var objective = teamSlot == null ? null : scoreboard.getDisplayObjective(teamSlot);
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return null;
        }
        int widest = mc.font.width(objective.getDisplayName());
        var format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        var scores = scoreboard.listPlayerScores(objective).stream()
                .filter(score -> !score.isHidden())
                .sorted(SIDEBAR_ORDER)
                .limit(15)
                .toList();
        for (var score : scores) {
            var name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), score.ownerName());
            int valueWidth = mc.font.width(score.formatValue(format));
            int width = mc.font.width(name) + (valueWidth == 0 ? 0 : mc.font.width(": ") + valueWidth);
            widest = Math.max(widest, width);
        }
        return new Sidebar(widest, Math.max(1, scores.size()));
    }

    static boolean isBeneficial(MobEffectInstance effect) {
        return effect.getEffect().value().isBeneficial();
    }

    static void addFadingLayers(Map<RenderType, RenderType> layers, Supplier<RenderType> mippedTranslucent) {
        RenderType translucent = NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get();
        layers.put(NeoForgeRenderTypes.ITEM_LAYERED_SOLID.get(), translucent);
        layers.put(NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT.get(), translucent);
        layers.put(NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT_MIPPED.get(), mippedTranslucent.get());
    }

    static void flipAndScaleItem(PoseStack pose, float scale) {
        pose.scale(scale, -scale, scale);
    }
}
