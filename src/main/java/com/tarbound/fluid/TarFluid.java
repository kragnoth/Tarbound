package com.tarbound.fluid;

import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Tar spreads like lava: slow and short in the Overworld, faster and further where it's hot enough
 * (the Nether, or any dimension vanilla marks "ultra warm").
 */
public final class TarFluid {
    private TarFluid() {}

    private static boolean hot(LevelReader level) {
        return level.dimensionType().ultraWarm();
    }

    public static class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return hot(level) ? 10 : 30;
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return hot(level) ? 4 : 2;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return hot(level) ? 1 : 2;
        }
    }

    public static class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return hot(level) ? 10 : 30;
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return hot(level) ? 4 : 2;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return hot(level) ? 1 : 2;
        }
    }
}
