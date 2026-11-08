package com.joplayx.client.state;

import com.joplayx.VillagerReroll;
import com.joplayx.client.Texts;
import com.joplayx.client.VillagerRerollClient;
import com.joplayx.client.config.RerollerConfig;
import com.joplayx.client.state.LecternBreakGate.BlockKind;
import com.joplayx.client.state.LecternBreakGate.Observation;
import com.joplayx.client.state.LecternBreakGate.Phase;
import com.joplayx.client.util.HotbarUtil;
import com.joplayx.client.util.OffersStore;
import com.joplayx.client.util.PredictionLedger;
import com.joplayx.client.util.TradeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * State machine driving the reroll loop.
 *
 * Breaking a lectern does not advance the loop when the destroy packet is sent.
 * 26.3 keeps that position in {@code BlockStatePredictionHandler} until
 * {@code ClientLevel.handleBlockChangedAck} settles it. Only a settled air block
 * counts as the lectern being gone.
 */
public class RerollController {

	private enum StopKind { PLAYER, EMERGENCY, ERROR }

	/** World id passed to the break gate for one run. A new client level aborts first. */
	private static final int SESSION_WORLD = 1;

	private RerollState state = RerollState.IDLE;
	private String statusKey = "status.villager-reroll.idle";
	private Object[] statusArgs = new Object[0];
	private String lastTradeDescription = "";
	private String lastOfferSeen = "";
	private java.util.List<TradeUtil.TargetStatus> lastStatuses = java.util.List.of();
	private int attempts = 0;
	private String errorReason = "";

	private int tickDelay = 0;
	private int professionWaitTicks = 0;
	private static final int MAX_PROFESSION_WAIT = 200;
	private int screenWaitTicks = 0;
	private static final int MAX_SCREEN_WAIT = 100;
	private int pickupWaitTicks = 0;
	private static final int MAX_PICKUP_WAIT = 60;
	private static final int MIN_AXE_DURABILITY = 10;

	private final LecternBreakGate breakGate = new LecternBreakGate();
	private int swungAttempt = -1;
	private BlockKind lastBreakView = BlockKind.LECTERN;
	private ClientLevel sessionLevel;

	// Auto-pickup walk: sneak speed is ~1.3 blocks/s, so 200 ticks covers ~13 blocks
	private static final int MAX_WALK_TICKS = 200;
	private static final double HOME_TOLERANCE = 0.2;
	private static final double ITEM_SEARCH_RADIUS = 6.0;
	private int walkTicks = 0;
	private int walkItemId = -1;
	private Vec3 homePos = null;
	private float homeYaw = 0f;
	private float homePitch = 0f;

	// Store by entity ID — safe across ticks on the client
	private int targetEntityId = -1;
	// Villager picked by the player (look at it + key or /reroll villager); -1 = use nearest
	private int selectedVillagerId = -1;

	public RerollState getState() { return state; }
	public String getStatusMessage() { return Texts.str(statusKey, statusArgs); }
	public String getLastTradeDescription() { return lastTradeDescription; }
	public String getLastOfferSeen() { return lastOfferSeen; }
	public java.util.List<TradeUtil.TargetStatus> getLastStatuses() { return lastStatuses; }
	public int getAttempts() { return attempts; }
	public String getErrorReason() { return errorReason; }
	public int getSelectedVillagerId() { return selectedVillagerId; }

	public boolean isRunning() {
		return state != RerollState.IDLE && state != RerollState.FOUND && state != RerollState.ERROR;
	}

	public void toggleStartStop(Minecraft client) {
		if (isRunning()) stop(client, StopKind.PLAYER, "reason.villager-reroll.player");
		else start(client);
	}

	public void stopByPlayer(Minecraft client) {
		if (isRunning()) stop(client, StopKind.PLAYER, "reason.villager-reroll.player");
	}

	public void emergencyStop(Minecraft client) {
		stop(client, StopKind.EMERGENCY, "reason.villager-reroll.emergency");
		if (client.player != null) {
			client.player.sendSystemMessage(Texts.prefixed("message.villager-reroll.emergency"));
		}
	}

	/** Locks the reroller onto this villager instead of "nearest within radius". */
	public void selectVillager(Villager villager) {
		selectedVillagerId = villager.getId();
		if (isRunning()) targetEntityId = selectedVillagerId;
	}

	public void clearSelectedVillager() {
		selectedVillagerId = -1;
	}

	/** The selected villager if it's still loaded and alive, otherwise null. */
	public Villager getSelectedVillager(ClientLevel level) {
		if (selectedVillagerId == -1 || level == null) return null;
		Entity e = level.getEntity(selectedVillagerId);
		return (e instanceof Villager v && v.isAlive()) ? v : null;
	}

	public void tick(Minecraft client) {
		if (!isRunning()) return;

		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			stop(client, StopKind.ERROR, "reason.villager-reroll.world_changed");
			return;
		}
		if (sessionLevel != null && level != sessionLevel) {
			stop(client, StopKind.ERROR, "reason.villager-reroll.world_changed");
			return;
		}

		if (tickDelay > 0) { tickDelay--; return; }

		switch (state) {

			case PRE_CHECK -> {
				status("status.villager-reroll.checking");
				if (HotbarUtil.findLectern(player) == HotbarUtil.LecternLocation.NONE) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.no_lectern_item"); return;
				}
				if (RerollerConfig.get().targets.isEmpty()) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.no_targets"); return;
				}
				if (!RerollerConfig.get().hasLecternPos()) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.no_pos",
							VillagerRerollClient.setPositionKey.getTranslatedKeyMessage());
					return;
				}
				Villager v;
				if (selectedVillagerId != -1) {
					v = getSelectedVillager(level);
					if (v == null) {
						selectedVillagerId = -1;
						stop(client, StopKind.ERROR, "reason.villager-reroll.villager_gone_selected"); return;
					}
				} else {
					v = findNearestVillager(player, level);
					if (v == null) {
						stop(client, StopKind.ERROR, "reason.villager-reroll.no_villager",
								RerollerConfig.get().searchRadius);
						return;
					}
				}
				targetEntityId = v.getId();
				setState(RerollState.PLACE_LECTERN, "status.villager-reroll.placing", 5);
			}

			case PLACE_LECTERN -> {
				BlockPos pos = RerollerConfig.get().lecternPos();
				if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.LECTERN)) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.blocked"); return;
				}
				if (level.getBlockState(pos).isAir()) {
					InteractionHand hand = HotbarUtil.prepareLectern(client, player);
					if (hand == null) {
						stop(client, StopKind.ERROR, "reason.villager-reroll.no_lecterns_left"); return;
					}
					BlockHitResult hit = new BlockHitResult(
							Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false
					);
					if (client.gameMode != null) {
						client.gameMode.useItemOn(player, hand, hit);
					}
				}
				professionWaitTicks = 0;
				setState(RerollState.WAIT_FOR_PROFESSION, "status.villager-reroll.wait_profession", 20);
			}

			case WAIT_FOR_PROFESSION -> {
				professionWaitTicks++;
				Entity entity = level.getEntity(targetEntityId);
				if (entity == null || !entity.isAlive()) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.villager_disappeared"); return;
				}

				if (entity instanceof Villager villager) {
					villager.getVillagerData().profession().unwrapKey().ifPresent(profKey -> {
						if (!profKey.identifier().getPath().equals("none")) {
							setState(RerollState.OPEN_VILLAGER, "status.villager-reroll.opening", 10);
						}
					});
				}

				if (state == RerollState.WAIT_FOR_PROFESSION && professionWaitTicks >= MAX_PROFESSION_WAIT) {
					beginBreak(RerollState.BREAK_LECTERN, "status.villager-reroll.profession_timeout", 5);
				}
			}

			case OPEN_VILLAGER -> {
				Entity entity = level.getEntity(targetEntityId);
				if (entity == null || !entity.isAlive()) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.villager_disappeared"); return;
				}
				// Drop the previous attempt's offers so WAIT_FOR_SCREEN only accepts a fresh packet
				OffersStore.clear();
				screenWaitTicks = 0;
				if (client.gameMode != null) {
					EntityHitResult entityHit = new EntityHitResult(entity);
					client.gameMode.interact(player, entity, entityHit, InteractionHand.MAIN_HAND);
				}
				setState(RerollState.WAIT_FOR_SCREEN, "status.villager-reroll.wait_screen", 10);
			}

			case WAIT_FOR_SCREEN -> {
				if (OffersStore.get() != null) {
					setState(RerollState.READ_TRADES, "status.villager-reroll.reading", 2);
				} else if ((screenWaitTicks += 5) >= MAX_SCREEN_WAIT) {
					setState(RerollState.OPEN_VILLAGER, "status.villager-reroll.no_screen", 5);
				} else {
					tickDelay = 5;
				}
			}

			case READ_TRADES -> {
				TradeUtil.TradeResult result = TradeUtil.checkTrades(RerollerConfig.get());
				attempts++;
				lastStatuses = result.statuses();
				lastOfferSeen = result.lastOfferSeen();

				if (result.anyFound()) {
					RerollerConfig.Config cfg = RerollerConfig.get();
					RerollerConfig.EnchantTarget found = result.foundTarget();
					String desc = result.descriptionFor(found);
					lastTradeDescription = desc;

					setState(RerollState.FOUND, "status.villager-reroll.found", 0, desc);
					player.closeContainer();
					player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

					cfg.targets.remove(found);
					RerollerConfig.save();
					selectedVillagerId = -1;

					if (cfg.targets.isEmpty()) {
						player.sendSystemMessage(Texts.prefixed(
								"message.villager-reroll.found_all", desc, attempts));
					} else {
						player.sendSystemMessage(Texts.prefixed(
								"message.villager-reroll.found_more", desc, attempts, cfg.targets.size()));
					}
				} else {
					setState(RerollState.CLOSE_SCREEN, "status.villager-reroll.no_match", 2);
				}
			}

			case CLOSE_SCREEN -> {
				if (player.containerMenu instanceof MerchantMenu) player.closeContainer();
				setState(RerollState.WAIT_AFTER_CLOSE, "status.villager-reroll.wait_break", RerollerConfig.get().closeDelayTicks);
			}

			case WAIT_AFTER_CLOSE -> beginBreak(RerollState.BREAK_LECTERN, "status.villager-reroll.breaking", 2);

			case BREAK_LECTERN, WAIT_BREAK_COMPLETE -> handleBreak(client, player, level);

			case WAIT_BEFORE_RETRY -> {
				if (HotbarUtil.findLectern(player) == HotbarUtil.LecternLocation.NONE) {
					if (pickupWaitTicks < MAX_PICKUP_WAIT) {
						pickupWaitTicks += 5;
						status("status.villager-reroll.pickup_wait");
						tickDelay = 5;
						return;
					}
					ItemEntity dropped = findDroppedLectern(player, level);
					if (dropped == null) {
						stop(client, StopKind.ERROR, "reason.villager-reroll.no_lecterns_left");
					} else if (!RerollerConfig.get().autoPickup) {
						stop(client, StopKind.ERROR, "reason.villager-reroll.pickup_out_of_range");
					} else {
						homePos = player.position();
						homeYaw = player.getYRot();
						homePitch = player.getXRot();
						walkItemId = dropped.getId();
						walkTicks = 0;
						setState(RerollState.WALK_TO_ITEM, "status.villager-reroll.walking", 0);
					}
					return;
				}
				setState(RerollState.PLACE_LECTERN, "status.villager-reroll.attempt", 5, attempts + 1);
			}

			case WALK_TO_ITEM -> {
				if (HotbarUtil.findLectern(player) != HotbarUtil.LecternLocation.NONE) {
					walkTicks = 0;
					setState(RerollState.WALK_BACK, "status.villager-reroll.walking_back", 0);
					return;
				}
				Entity item = level.getEntity(walkItemId);
				if (!(item instanceof ItemEntity) || !item.isAlive()) {
					ItemEntity other = findDroppedLectern(player, level);
					if (other == null) {
						releaseMovement(client);
						stop(client, StopKind.ERROR, "reason.villager-reroll.item_lost");
						return;
					}
					walkItemId = other.getId();
					item = other;
				}
				if (++walkTicks > MAX_WALK_TICKS) {
					releaseMovement(client);
					stop(client, StopKind.ERROR, "reason.villager-reroll.walk_failed");
					return;
				}
				walkToward(client, player, item.position(), 0.0);
			}

			case WALK_BACK -> {
				if (++walkTicks > MAX_WALK_TICKS) {
					releaseMovement(client);
					stop(client, StopKind.ERROR, "reason.villager-reroll.walk_back_failed");
					return;
				}
				if (walkToward(client, player, homePos, HOME_TOLERANCE)) {
					releaseMovement(client);
					player.setYRot(homeYaw);
					player.setYHeadRot(homeYaw);
					player.setXRot(homePitch);
					setState(RerollState.PLACE_LECTERN, "status.villager-reroll.attempt", 10, attempts + 1);
				}
			}

			default -> stop(client, StopKind.ERROR, "reason.villager-reroll.unknown_state", state);
		}
	}

	/**
	 * Swing only while the real block is still a lectern. Visible air while the
	 * position is in the prediction ledger is not a cue to place or open trades.
	 */
	private void handleBreak(Minecraft client, LocalPlayer player, ClientLevel level) {
		BlockPos pos = RerollerConfig.get().lecternPos();
		if (pos == null) {
			stop(client, StopKind.ERROR, "reason.villager-reroll.no_pos",
					VillagerRerollClient.setPositionKey.getTranslatedKeyMessage());
			return;
		}
		if (breakGate.phase() == Phase.IDLE) breakGate.begin(SESSION_WORLD);

		Phase before = breakGate.phase();
		BlockKind kind = classify(level, pos);
		boolean pending = PredictionLedger.isPending(level, pos);
		boolean swung = false;

		if (before == Phase.SWINGING && kind == BlockKind.LECTERN && client.gameMode != null) {
			if (swungAttempt != breakGate.attempt()) {
				swungAttempt = breakGate.attempt();
				if (HotbarUtil.selectBestAxe(player)
						&& HotbarUtil.isSelectedItemLowDurability(player, MIN_AXE_DURABILITY)) {
					stop(client, StopKind.ERROR, "reason.villager-reroll.axe_low");
					return;
				}
				client.gameMode.startDestroyBlock(pos, Direction.UP);
			} else {
				client.gameMode.continueDestroyBlock(pos, Direction.UP);
			}
			swung = true;
			kind = classify(level, pos);
			pending = PredictionLedger.isPending(level, pos);
		}
		lastBreakView = kind;

		Phase after = breakGate.observe(new Observation(SESSION_WORLD, kind, pending, swung));
		switch (after) {
			case SWINGING -> ensureState(RerollState.BREAK_LECTERN, "status.villager-reroll.breaking");
			case AWAIT_SETTLE -> ensureState(RerollState.WAIT_BREAK_COMPLETE,
					kind == BlockKind.UNLOADED && !pending
							? "status.villager-reroll.chunk_wait"
							: "status.villager-reroll.await_server");
			case RETRY_PAUSE -> ensureState(RerollState.WAIT_BREAK_COMPLETE,
					"status.villager-reroll.retry_break", breakGate.attempt(), LecternBreakGate.MAX_ATTEMPTS);
			case CONFIRMED -> {
				if (client.gameMode != null) client.gameMode.stopDestroyBlock();
				breakGate.reset();
				swungAttempt = -1;
				pickupWaitTicks = 0;
				setState(RerollState.WAIT_BEFORE_RETRY, "status.villager-reroll.broken_wait",
						RerollerConfig.get().retryDelayTicks);
			}
			case OCCUPIED -> stop(client, StopKind.ERROR, "reason.villager-reroll.break_occupied");
			case UNCONFIRMED -> stop(client, StopKind.ERROR,
					lastBreakView == BlockKind.UNLOADED
							? "reason.villager-reroll.chunk_unloaded"
							: "reason.villager-reroll.break_unconfirmed");
			case CANCELLED -> stop(client, StopKind.ERROR, "reason.villager-reroll.break_cancelled");
			case FAILED_BREAK -> stop(client, StopKind.ERROR, "reason.villager-reroll.break_failed");
			case IDLE -> stop(client, StopKind.ERROR, "reason.villager-reroll.unknown_state", after);
		}
	}

	private void beginBreak(RerollState newState, String key, int delay) {
		breakGate.reset();
		swungAttempt = -1;
		setState(newState, key, delay);
	}

	private static BlockKind classify(ClientLevel level, BlockPos pos) {
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
			return BlockKind.UNLOADED;
		}
		BlockState block = level.getBlockState(pos);
		if (block.is(Blocks.LECTERN)) return BlockKind.LECTERN;
		if (block.isAir()) return BlockKind.AIR;
		return BlockKind.OTHER;
	}

	public void start(Minecraft client) {
		if (client.level == null || client.player == null) return;
		attempts = 0;
		errorReason = "";
		lastTradeDescription = "";
		lastOfferSeen = "";
		lastStatuses = List.of();
		targetEntityId = -1;
		breakGate.reset();
		swungAttempt = -1;
		sessionLevel = client.level;
		OffersStore.clear();
		setState(RerollState.PRE_CHECK, "status.villager-reroll.starting", 2);
		client.player.sendSystemMessage(Texts.prefixed(
				"message.villager-reroll.started",
				VillagerRerollClient.emergencyStopKey.getTranslatedKeyMessage()));
	}

	private void stop(Minecraft client, StopKind kind, String key, Object... args) {
		if (state == RerollState.WALK_TO_ITEM || state == RerollState.WALK_BACK) releaseMovement(client);
		if (client.gameMode != null) client.gameMode.stopDestroyBlock();
		breakGate.reset();
		swungAttempt = -1;
		walkTicks = 0;
		sessionLevel = null;
		status(key, args);
		errorReason = getStatusMessage();
		state = (kind == StopKind.PLAYER || kind == StopKind.EMERGENCY)
				? RerollState.IDLE : RerollState.ERROR;
		tickDelay = 0;
		VillagerReroll.LOGGER.info("[Reroller] Stopped: {}", errorReason);
		if (client.player != null) {
			client.player.sendSystemMessage(Texts.prefixed("message.villager-reroll.stopped", Texts.tr(key, args)));
		}
	}

	private void setState(RerollState newState, String key, int delay, Object... args) {
		state = newState;
		status(key, args);
		tickDelay = delay;
		VillagerReroll.LOGGER.debug("[Reroller] -> {} | {}", newState, key);
	}

	private void ensureState(RerollState newState, String key, Object... args) {
		status(key, args);
		if (state != newState) {
			state = newState;
			tickDelay = 0;
			VillagerReroll.LOGGER.debug("[Reroller] -> {} | {}", newState, key);
		}
	}

	private void status(String key, Object... args) {
		statusKey = key;
		statusArgs = args == null ? new Object[0] : args;
	}

	/** The dropped lectern item closest to the player, searched around the lectern spot. */
	private ItemEntity findDroppedLectern(LocalPlayer player, ClientLevel level) {
		BlockPos pos = RerollerConfig.get().lecternPos();
		if (pos == null) return null;
		AABB box = new AABB(pos).inflate(ITEM_SEARCH_RADIUS);
		return level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && HotbarUtil.isLectern(e.getItem()))
				.stream()
				.min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
				.orElse(null);
	}

	/**
	 * Steers the player toward target by facing it and holding forward + sneak.
	 * Sneaking keeps the walk slow enough to stop on the spot and stops the player
	 * from walking off ledges. Returns true once within tolerance (horizontally).
	 */
	private boolean walkToward(Minecraft client, LocalPlayer player, Vec3 target, double tolerance) {
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist <= tolerance) return true;

		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		client.options.keyUp.setDown(true);
		client.options.keyShift.setDown(true);
		return false;
	}

	private void releaseMovement(Minecraft client) {
		client.options.keyUp.setDown(false);
		client.options.keyShift.setDown(false);
	}

	private Villager findNearestVillager(LocalPlayer player, ClientLevel level) {
		AABB box = player.getBoundingBox().inflate(RerollerConfig.get().searchRadius);
		List<Entity> entities = level.getEntities(player, box,
				e -> e instanceof Villager && e.isAlive());
		return entities.stream()
				.map(e -> (Villager) e)
				.min((a, b) -> Double.compare(
						a.position().distanceToSqr(player.position()),
						b.position().distanceToSqr(player.position())))
				.orElse(null);
	}
}
