package com.tarbound.compat.jade;

import com.tarbound.block.ReliquaryBlock;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.block.ScryingFontBlock;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.block.TarAntennaBlock;
import com.tarbound.block.TarAntennaBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (optional): what the radio, Reliquaries and antennas are doing, at a glance. Each provider
 * sends a little server data (counts, tuning, range) and draws it client side. Jade finds this class
 * by its annotation and only loads it when Jade is installed.
 */
@WailaPlugin
public class TarboundJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FontJadeProvider.INSTANCE, ScryingFontBlockEntity.class);
        registration.registerBlockDataProvider(ReliquaryJadeProvider.INSTANCE, ReliquaryBlockEntity.class);
        registration.registerBlockDataProvider(AntennaJadeProvider.INSTANCE, TarAntennaBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FontJadeProvider.INSTANCE, ScryingFontBlock.class);
        registration.registerBlockComponent(ReliquaryJadeProvider.INSTANCE, ReliquaryBlock.class);
        registration.registerBlockComponent(AntennaJadeProvider.INSTANCE, TarAntennaBlock.class);
    }
}
