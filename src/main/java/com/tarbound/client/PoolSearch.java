package com.tarbound.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The radio's search box, with the recipe viewers' prefixes:
 * <ul>
 *   <li>{@code @mod}: mod id or name</li>
 *   <li>{@code #words}: tooltip text (JEI); with only EMI installed, {@code #} is a tag and {@code $} the tooltip, as in EMI</li>
 *   <li>{@code $tag}: item tag, e.g. {@code $c:ingots} (JEI)</li>
 *   <li>{@code ^color}: main colors of the icon, e.g. {@code ^red}</li>
 *   <li>{@code &id}: registry id, e.g. {@code &minecraft:oak}</li>
 *   <li>plain words: the name</li>
 * </ul>
 * Words are ANDed; {@code |} separates alternatives (OR); {@code -} in front excludes; {@code "two words"} keeps a space.
 */
final class PoolSearch {
    enum Field { NAME, MOD, TOOLTIP, TAG, COLOR, ID }

    private record Term(Field field, String text, boolean negate) {}

    private static final Pattern TOKEN = Pattern.compile("(-?)([@#$^&]?)(\"[^\"]*\"?|\\S+)");

    /** Alternatives (OR), each a list of terms that must all match (AND). */
    private final List<List<Term>> groups;
    private final boolean empty;

    private PoolSearch(List<List<Term>> groups) {
        this.groups = groups;
        boolean e = true;
        for (List<Term> g : groups) {
            e &= g.isEmpty();
        }
        this.empty = e;
    }

    boolean isEmpty() {
        return empty;
    }

    /** @param emiStyle {@code #} means tag and {@code $} tooltip (EMI) instead of the other way round (JEI) */
    static PoolSearch parse(String query, boolean emiStyle) {
        List<List<Term>> groups = new ArrayList<>();
        for (String part : query.toLowerCase(Locale.ROOT).split("\\|")) {
            List<Term> terms = new ArrayList<>();
            Matcher m = TOKEN.matcher(part);
            while (m.find()) {
                String text = m.group(3);
                if (text.startsWith("\"")) {
                    text = text.substring(1, text.endsWith("\"") && text.length() > 1 ? text.length() - 1 : text.length());
                }
                Field f = switch (m.group(2)) {
                    case "@" -> Field.MOD;
                    case "#" -> emiStyle ? Field.TAG : Field.TOOLTIP;
                    case "$" -> emiStyle ? Field.TOOLTIP : Field.TAG;
                    case "^" -> Field.COLOR;
                    case "&" -> Field.ID;
                    default -> Field.NAME;
                };
                if (!text.isEmpty()) {
                    terms.add(new Term(f, text, !m.group(1).isEmpty()));
                }
            }
            groups.add(terms);
        }
        return new PoolSearch(groups);
    }

    boolean matches(Info info) {
        if (empty) {
            return true;
        }
        for (List<Term> g : groups) {
            boolean all = true;
            for (Term t : g) {
                if (info.has(t.field(), t.text()) == t.negate()) {
                    all = false;
                    break;
                }
            }
            if (all && !g.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * What one pool entry can be searched by, each part worked out the first time a search needs it
     * (tooltips and icon colors aren't free) and then kept.
     */
    static final class Info {
        private final ItemStack item;
        private final FluidStack fluid;
        private final String name;
        @Nullable
        private String mod;
        @Nullable
        private String id;
        @Nullable
        private String tooltip;
        @Nullable
        private List<String> tags;
        @Nullable
        private Set<String> colors;

        Info(ItemStack item, FluidStack fluid, String lowerName) {
            this.item = item;
            this.fluid = fluid;
            this.name = lowerName;
        }

        private boolean isFluid() {
            return !fluid.isEmpty();
        }

        boolean has(Field f, String text) {
            return switch (f) {
                case NAME -> name.contains(text);
                case MOD -> mod().contains(text);
                case ID -> id().contains(text);
                case TOOLTIP -> tooltip().contains(text);
                case TAG -> {
                    for (String t : tags()) {
                        if (t.contains(text)) {
                            yield true;
                        }
                    }
                    yield false;
                }
                case COLOR -> {
                    for (String c : colors()) {
                        if (c.startsWith(text) || c.replace("_", " ").startsWith(text)) {
                            yield true;
                        }
                    }
                    yield false;
                }
            };
        }

        private ResourceLocation key() {
            return isFluid() ? BuiltInRegistries.FLUID.getKey(fluid.getFluid()) : BuiltInRegistries.ITEM.getKey(item.getItem());
        }

        private String mod() {
            if (mod == null) {
                String ns = key().getNamespace();
                String display = ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
                mod = (ns + " " + display).toLowerCase(Locale.ROOT);
            }
            return mod;
        }

        private String id() {
            if (id == null) {
                id = key().toString();
            }
            return id;
        }

        private String tooltip() {
            if (tooltip == null) {
                StringBuilder sb = new StringBuilder(name);
                if (!isFluid()) {
                    Minecraft mc = Minecraft.getInstance();
                    try {
                        for (Component line : item.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL)) {
                            sb.append('\n').append(line.getString().toLowerCase(Locale.ROOT));
                        }
                    } catch (RuntimeException ignored) {
                        // a mod's tooltip code failed: search the name only
                    }
                }
                tooltip = sb.toString();
            }
            return tooltip;
        }

        private List<String> tags() {
            if (tags == null) {
                List<String> list = new ArrayList<>();
                (isFluid() ? fluid.getFluid().builtInRegistryHolder().tags() : item.getItem().builtInRegistryHolder().tags())
                        .forEach(t -> list.add(t.location().toString()));
                if (!isFluid() && item.getItem() instanceof BlockItem bi) {
                    bi.getBlock().builtInRegistryHolder().tags().forEach(t -> list.add(t.location().toString()));
                }
                tags = list;
            }
            return tags;
        }

        private Set<String> colors() {
            if (colors == null) {
                colors = new LinkedHashSet<>();
                try {
                    if (isFluid()) {
                        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
                        int tint = ext.getTintColor(fluid) & 0xFFFFFF;
                        if (tint != 0xFFFFFF) {
                            colors.add(nearest(tint).getName());
                        } else {
                            TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                                    .apply(ext.getStillTexture(fluid));
                            colors.addAll(dominant(s));
                        }
                    } else {
                        TextureAtlasSprite s = Minecraft.getInstance().getItemRenderer().getModel(item, null, null, 0).getParticleIcon();
                        colors.addAll(dominant(s));
                    }
                } catch (RuntimeException ignored) {
                    // no texture to look at
                }
                // A color in the id counts too (red_wool, blue_ice), whatever the texture says.
                String path = key().getPath();
                for (DyeColor d : DyeColor.values()) {
                    if (path.contains(d.getName())) {
                        colors.add(d.getName());
                    }
                }
            }
            return colors;
        }

        /** The one or two dye colors most of the sprite's visible pixels are nearest to. */
        private static List<String> dominant(TextureAtlasSprite sprite) {
            var img = sprite.contents().getOriginalImage();
            int w = sprite.contents().width();
            int h = sprite.contents().height();
            Map<DyeColor, Integer> votes = new HashMap<>();
            int total = 0;
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int abgr = img.getPixelRGBA(x, y);
                    if ((abgr >>> 24) < 128) {
                        continue;
                    }
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    votes.merge(nearest(rgb), 1, Integer::sum);
                    total++;
                }
            }
            List<String> out = new ArrayList<>();
            if (total == 0) {
                return out;
            }
            int minShare = Math.max(1, total / 5);
            votes.entrySet().stream()
                    .sorted(Map.Entry.<DyeColor, Integer>comparingByValue().reversed())
                    .limit(2)
                    .filter(e -> e.getValue() >= minShare)
                    .forEach(e -> out.add(e.getKey().getName()));
            return out;
        }

        private static DyeColor nearest(int rgb) {
            int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
            DyeColor best = DyeColor.WHITE;
            long bestD = Long.MAX_VALUE;
            for (DyeColor d : DyeColor.values()) {
                int c = d.getTextureDiffuseColor();
                int dr = (c >> 16 & 0xFF) - r, dg = (c >> 8 & 0xFF) - g, db = (c & 0xFF) - b;
                // weighted toward how eyes see it
                long dist = 3L * dr * dr + 4L * dg * dg + 2L * db * db;
                if (dist < bestD) {
                    bestD = dist;
                    best = d;
                }
            }
            return best;
        }
    }
}
