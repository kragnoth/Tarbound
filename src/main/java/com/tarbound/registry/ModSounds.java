package com.tarbound.registry;

import com.tarbound.Tarbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Tarbound.MODID);

    /** Ambient crackle and a ghost of a carrier tone; plays now and then from an idle font. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FONT_STATIC = register("block.scrying_font.static");
    /** A tuning sweep when someone opens the font. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FONT_TUNE = register("block.scrying_font.tune");

    /** Tar swallowing an item into the pool. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAR_GULP = register("block.tar.gulp");
    /** Tar spitting an item back up (the pool couldn't take it). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAR_SPIT = register("block.tar.spit");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Tarbound.id(name)));
    }

    private ModSounds() {}
}
