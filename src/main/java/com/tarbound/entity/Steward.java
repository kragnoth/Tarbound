package com.tarbound.entity;

import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.block.ShadeBlockEntity;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.ContractJob;
import com.tarbound.contract.CraftOrder;
import com.tarbound.contract.CraftPlanner;
import com.tarbound.contract.OrderVessel;
import com.tarbound.network.FluidKey;
import com.tarbound.network.ItemKey;
import com.tarbound.network.RadioRegistry;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;

/**
 * The Steward: a spirit bound into a husk of tar, with an observer for a head, that keeps a list of things in stock.
 * It stands where it's set down and never moves. Every second it counts what its radio's pool holds of each item on
 * its list, and when one runs under the amount to keep it asks the radio's contracts for more: a one-step craft
 * becomes an ordinary job, a craft with sub-crafts becomes an order the Steward watches over itself (it holds that
 * order's stock the way a Shade does, so it needs no Shade, but it watches one order at a time).
 *
 * <p>Right-click it for its list; shift-right-click with an empty hand to pick it up (list and tuning kept). It takes
 * no harm but from a creative player or {@code /kill}, and tar doesn't hurt it (it's made of the stuff).
 */
public class Steward extends PathfinderMob implements OrderVessel {
    /** Most steps (sub-crafts plus the thing itself) an order it watches may have: as much as the strongest Shade. */
    public static final int CAPACITY = ShadeBlockEntity.capacityOf(ShadeBlockEntity.MAX_RANK);
    /** Ticks between stock checks. */
    private static final int CHECK_EVERY = 20;
    /** Ticks a target that couldn't be started waits before it's planned again (planning isn't free). */
    private static final int RETRY_AFTER = 100;

    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(Steward.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions TAR_BEAD = new DustParticleOptions(new Vector3f(0.06F, 0.05F, 0.05F), 0.7F);

    /** What a target is up to, as the screen shows it. */
    public enum Status {
        STOCKED,
        MAKING,
        SHORT,
        NO_CONTRACT,
        NO_SLOT,
        ORDER_BUSY,
        TOO_BIG,
        NO_RADIO;

        public static Status of(int i) {
            Status[] all = values();
            return i >= 0 && i < all.length ? all[i] : NO_RADIO;
        }
    }

    @Nullable
    private GlobalPos link;
    @Nullable
    private UUID linkId;
    private final List<StockTarget> targets = new ArrayList<>();

    // The order it's watching, and what it holds for it (saved).
    private int orderId;
    private final Map<ItemKey, Long> held = new LinkedHashMap<>();
    private final Map<FluidKey, Long> heldFluids = new LinkedHashMap<>();

    // Server, not saved: the last count and status of each target, and when each may be planned again.
    private long[] have = new long[0];
    private Status[] status = new Status[0];
    private long[] retryAt = new long[0];

    public Steward(EntityType<? extends Steward> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WORKING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.6F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /** Client: whether it has work running (its lower hands move). */
    public boolean working() {
        return entityData.get(DATA_WORKING);
    }

    // ------------------------------------------------------------------------------------------ never moves, never hurt

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public void push(double x, double y, double z) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        if (!creative && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            dropOrder();
            boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
            if (!creative) {
                spawnAtLocation(toItem());
            }
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {}

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    /** It turns its head to watch, never its body: it faces the way it was set down. */
    @Override
    protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new net.minecraft.world.entity.ai.control.BodyRotationControl(this) {
            @Override
            public void clientTick() {}
        };
    }

    // ------------------------------------------------------------------------------------------ tuning and the list

    @Nullable
    public GlobalPos link() {
        return link;
    }

    public void tuneTo(@Nullable GlobalPos link, @Nullable UUID linkId) {
        this.link = link;
        this.linkId = linkId;
    }

    /** The loaded radio it's tuned to, or null. */
    @Nullable
    public ScryingFontBlockEntity radio() {
        Level level = level();
        if (link == null || !link.dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos at = linkId != null ? RadioRegistry.find(level, linkId) : null;
        if (at == null) {
            at = link.pos();
        }
        if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof ScryingFontBlockEntity font)) {
            return null;
        }
        return linkId == null || linkId.equals(font.radioId()) ? font : null;
    }

    public List<StockTarget> targets() {
        return List.copyOf(targets);
    }

    public void setTargets(List<StockTarget> list) {
        targets.clear();
        for (StockTarget t : list) {
            if (targets.size() < StockTarget.MAX && !t.item().isEmpty() && indexOf(t.item()) < 0) {
                targets.add(t);
            }
        }
        resized();
    }

    private int indexOf(ItemStack item) {
        for (int i = 0; i < targets.size(); i++) {
            if (ItemStack.isSameItemSameComponents(targets.get(i).item(), item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Server: an edit from the screen. Row {@code row} (or a new one, if past the end) becomes {@code item} kept at
     * {@code keep}; an empty item removes the row. An item already on the list elsewhere just has its amount set there.
     *
     * @return the row the item ended up in, or -1
     */
    public int edit(int row, ItemStack item, int keep) {
        if (item.isEmpty()) {
            if (row >= 0 && row < targets.size()) {
                targets.remove(row);
                resized();
            }
            return -1;
        }
        int existing = indexOf(item);
        int at;
        if (existing >= 0) {
            at = existing;
            targets.set(at, new StockTarget(item, keep));
        } else if (row >= 0 && row < targets.size()) {
            at = row;
            targets.set(at, new StockTarget(item, keep));
        } else if (targets.size() < StockTarget.MAX) {
            at = targets.size();
            targets.add(new StockTarget(item, keep));
        } else {
            return -1;
        }
        resized();
        retryAt[at] = 0;
        return at;
    }

    private void resized() {
        int n = targets.size();
        have = Arrays.copyOf(have, n);
        retryAt = new long[n];
        status = new Status[n];
        Arrays.fill(status, Status.NO_RADIO);
    }

    /** Server: what was last counted of each target. */
    public long have(int i) {
        return i < have.length ? have[i] : 0;
    }

    public Status status(int i) {
        return i < status.length && status[i] != null ? status[i] : Status.NO_RADIO;
    }

    // ------------------------------------------------------------------------------------------ keeping stock

    @Override
    public void tick() {
        super.tick();
        Level level = level();
        if (level.isClientSide) {
            leak();
            return;
        }
        if ((tickCount + getId()) % CHECK_EVERY == 0) {
            keepStock();
        }
    }

    /** Pins it in place: a Steward is set down, not walked. */
    @Override
    public void travel(Vec3 travel) {
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        super.travel(Vec3.ZERO);
    }

    /** Server: one round of counting, and at most one new job or order started. */
    void keepStock() {
        if (targets.size() != status.length) {
            resized();
        }
        ScryingFontBlockEntity font = radio();
        if (font == null) {
            Arrays.fill(status, Status.NO_RADIO);
            entityData.set(DATA_WORKING, false);
            return;
        }
        TarNetwork net = font.network();
        Map<ItemKey, Long> stock = net.countContents();
        long now = level().getGameTime();
        List<CraftPlanner.Source> sources = null;
        boolean started = false;
        boolean working = orderId != 0;
        for (int i = 0; i < targets.size(); i++) {
            StockTarget t = targets.get(i);
            ItemKey key = ItemKey.of(t.item());
            have[i] = stock.getOrDefault(key, 0L);
            if (making(font, key)) {
                status[i] = Status.MAKING;
                working = true;
                continue;
            }
            if (have[i] >= t.keep()) {
                status[i] = Status.STOCKED;
                continue;
            }
            if (started || now < retryAt[i]) {
                continue; // keeps the last status it had
            }
            if (sources == null) {
                sources = font.planSources(net);
            }
            Status s = start(font, net, sources, key, t.keep() - have[i]);
            status[i] = s;
            if (s == Status.MAKING) {
                started = true;
                working = true;
            } else {
                retryAt[i] = now + RETRY_AFTER;
            }
        }
        entityData.set(DATA_WORKING, working);
    }

    /** Whether the radio has a job or order running that makes this item (anyone's: no point doubling up). */
    private static boolean making(ScryingFontBlockEntity font, ItemKey key) {
        for (ContractJob job : font.jobs()) {
            if (!job.contract().tracksFluid() && key.matches(job.contract().primaryOutput())) {
                return true;
            }
        }
        for (CraftOrder o : font.orders()) {
            if (!o.output().isEmpty() && key.matches(o.output())) {
                return true;
            }
        }
        return false;
    }

    /** Plans and starts making up to {@code short_} of an item. */
    private Status start(ScryingFontBlockEntity font, TarNetwork net, List<CraftPlanner.Source> sources, ItemKey key, long shortBy) {
        CraftPlanner.Source source = null;
        for (CraftPlanner.Source s : sources) {
            ContractData c = s.contract();
            if (c.orderable() && !c.tracksFluid() && key.matches(c.primaryOutput())
                    && (source == null || (s.idle() > 0 && source.idle() == 0))) {
                source = s;
            }
        }
        if (source == null) {
            return Status.NO_CONTRACT;
        }
        if (font.workInUse() >= font.jobLimit()) {
            return Status.NO_SLOT;
        }
        int perOp = source.contract().perOp();
        int ops = (int) Math.max(1, Math.min(ScryingFontBlockEntity.maxOps(), (shortBy + perOp - 1) / perOp));
        List<List<ItemStack>> allInputs = new ArrayList<>();
        for (CraftPlanner.Source s : sources) {
            allInputs.addAll(s.contract().inputs());
        }
        Map<ItemKey, Long> counts = net.countForJobs(allInputs);
        Map<FluidKey, Long> fluids = net.countFluids();
        // As many as storage allows: halve until the plan works (the rest comes in later rounds).
        CraftPlanner.Plan plan = null;
        while (ops >= 1) {
            CraftPlanner.Plan p = CraftPlanner.plan(source, ops, sources, counts, fluids, font.planDepth(), font.planNodes());
            if (p.complete()) {
                plan = p;
                break;
            }
            if (ops == 1) {
                return p.truncated() ? Status.TOO_BIG : Status.SHORT;
            }
            ops /= 2;
        }
        if (plan == null) {
            return Status.SHORT;
        }
        if (plan.steps().size() == 1) {
            CraftPlanner.Step step = plan.steps().get(0);
            font.addJob(step.contract(), Math.max(1, Math.min(step.ops(), ScryingFontBlockEntity.maxOps())));
            return Status.MAKING;
        }
        if (orderId != 0) {
            return Status.ORDER_BUSY;
        }
        if (plan.steps().size() > CAPACITY) {
            return Status.TOO_BIG;
        }
        return font.startOrder(null, this, plan, net) ? Status.MAKING : Status.SHORT;
    }

    /** Ends its order (if any) on its radio, giving back what it held; with no radio, drops it. */
    private void dropOrder() {
        if (orderId == 0) {
            return;
        }
        ScryingFontBlockEntity font = radio();
        if (font != null && font.hasOrder(orderId)) {
            font.cancelOrder(orderId, this);
        } else {
            release(font != null ? font.network() : null);
        }
    }

    // ------------------------------------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        Level level = level();
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                dropOrder();
                ItemStack item = toItem();
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
                level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 0.6F);
                discard();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (radio() == null) {
                sp.displayClientMessage(Component.translatable(link == null ? "message.tarbound.steward.untuned"
                        : "message.tarbound.steward.no_radio").withStyle(ChatFormatting.GRAY), true);
            }
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> com.tarbound.menu.StewardMenu.server(id, inv, this), getDisplayName()),
                    buf -> buf.writeVarInt(getId()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** As an item: its tuning and list come along. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModItems.STEWARD.get());
        if (link != null) {
            stack.set(ModDataComponents.RADIO_LINK.get(), link);
            if (linkId != null) {
                stack.set(ModDataComponents.RADIO_ID.get(), linkId);
            }
        }
        if (!targets.isEmpty()) {
            stack.set(ModDataComponents.STEWARD_TARGETS.get(), List.copyOf(targets));
        }
        if (hasCustomName()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
        }
        return stack;
    }

    // ------------------------------------------------------------------------------------------ tar leaking (client)

    private void leak() {
        if (random.nextInt(6) != 0) {
            return;
        }
        // A crack on the torso or a hand: a bead clings there and now and then a drop lets go.
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double side = (random.nextDouble() - 0.5) * 0.55;
        double fwd = (random.nextDouble() - 0.5) * 0.25;
        double y = random.nextBoolean() ? 1.25 + random.nextDouble() * 0.75 : 0.6 + random.nextDouble() * 0.6;
        double x = getX() - Mth.sin(yaw) * fwd + Mth.cos(yaw) * side;
        double z = getZ() + Mth.cos(yaw) * fwd + Mth.sin(yaw) * side;
        level().addParticle(TAR_BEAD, x, getY() + y, z, 0, -0.01, 0);
        if (random.nextInt(3) == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, com.tarbound.registry.ModBlocks.TAR.get().defaultBlockState()),
                    x, getY() + y - 0.05, z, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------------------------------ OrderVessel

    @Override
    public void claim(int order) {
        orderId = order;
    }

    @Override
    public int orderId() {
        return orderId;
    }

    @Override
    public Map<ItemKey, Long> held() {
        return held;
    }

    @Override
    public Map<FluidKey, Long> heldFluids() {
        return heldFluids;
    }

    @Override
    public void hold(ItemStack stack) {
        if (!stack.isEmpty()) {
            held.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
        }
    }

    @Override
    public void hold(ItemKey key, long n) {
        if (n > 0) {
            held.merge(key, n, Long::sum);
        }
    }

    @Override
    public void holdFluid(FluidStack fluid) {
        if (!fluid.isEmpty()) {
            heldFluids.merge(FluidKey.of(fluid), (long) fluid.getAmount(), Long::sum);
        }
    }

    @Override
    public int take(ItemStack proto, int amount) {
        ItemKey key = ItemKey.of(proto);
        long h = held.getOrDefault(key, 0L);
        int got = (int) Math.min(h, amount);
        if (got > 0) {
            if (h - got <= 0) {
                held.remove(key);
            } else {
                held.put(key, h - got);
            }
        }
        return got;
    }

    @Override
    public int takeFluid(FluidStack fluid, int amount) {
        FluidKey key = FluidKey.of(fluid);
        long h = heldFluids.getOrDefault(key, 0L);
        int got = (int) Math.min(h, amount);
        if (got > 0) {
            if (h - got <= 0) {
                heldFluids.remove(key);
            } else {
                heldFluids.put(key, h - got);
            }
        }
        return got;
    }

    @Override
    public void release(@Nullable TarNetwork net) {
        for (Map.Entry<ItemKey, Long> e : held.entrySet()) {
            long n = e.getValue();
            ItemStack proto = e.getKey().proto();
            while (n > 0) {
                int chunk = (int) Math.min(n, proto.getMaxStackSize());
                ItemStack rest = net != null ? net.insert(proto.copyWithCount(chunk)) : proto.copyWithCount(chunk);
                if (!rest.isEmpty()) {
                    spawnAtLocation(rest);
                }
                n -= chunk;
            }
        }
        if (net != null) {
            for (Map.Entry<FluidKey, Long> e : heldFluids.entrySet()) {
                net.insertFluid(e.getKey().proto().copyWithAmount((int) Math.min(Integer.MAX_VALUE, e.getValue())), false);
            }
        }
        held.clear();
        heldFluids.clear();
        orderId = 0;
    }

    @Override
    public BlockPos vesselPos() {
        return blockPosition();
    }

    @Override
    public UUID vesselEntity() {
        return getUUID();
    }

    // ------------------------------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (link != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, link).result().ifPresent(t -> tag.put("link", t));
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
        var ops = registryAccess().createSerializationContext(NbtOps.INSTANCE);
        StockTarget.LIST_CODEC.encodeStart(ops, targets).result().ifPresent(t -> tag.put("targets", t));
        tag.putInt("order", orderId);
        ListTag items = new ListTag();
        for (Map.Entry<ItemKey, Long> e : held.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("item", e.getKey().proto().save(registryAccess()));
            t.putLong("n", e.getValue());
            items.add(t);
        }
        tag.put("held", items);
        ListTag fluids = new ListTag();
        for (Map.Entry<FluidKey, Long> e : heldFluids.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("fluid", e.getKey().proto().save(registryAccess()));
            t.putLong("n", e.getValue());
            fluids.add(t);
        }
        tag.put("held_fluids", fluids);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        link = tag.contains("link") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("link")).result().orElse(null) : null;
        linkId = tag.hasUUID("link_id") ? tag.getUUID("link_id") : null;
        var ops = registryAccess().createSerializationContext(NbtOps.INSTANCE);
        setTargets(tag.contains("targets") ? StockTarget.LIST_CODEC.parse(ops, tag.get("targets")).result().orElse(List.of()) : List.of());
        orderId = tag.getInt("order");
        held.clear();
        ListTag items = tag.getList("held", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            ItemStack s = ItemStack.parseOptional(registryAccess(), t.getCompound("item"));
            if (!s.isEmpty()) {
                held.merge(ItemKey.of(s), t.getLong("n"), Long::sum);
            }
        }
        heldFluids.clear();
        ListTag fl = tag.getList("held_fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag t = fl.getCompound(i);
            FluidStack f = FluidStack.parseOptional(registryAccess(), t.getCompound("fluid"));
            if (!f.isEmpty()) {
                heldFluids.merge(FluidKey.of(f), t.getLong("n"), Long::sum);
            }
        }
    }

    /** A Steward that lost its order (its radio forgot it while this was unloaded) gives the stock back. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && orderId != 0 && (tickCount + getId()) % 100 == 0) {
            ScryingFontBlockEntity font = radio();
            if (font != null && !font.hasOrder(orderId)) {
                release(font.network());
            }
        }
    }
}
