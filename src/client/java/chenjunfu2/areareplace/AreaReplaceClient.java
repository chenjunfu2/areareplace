package chenjunfu2.areareplace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.*;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class AreaReplaceClient implements ClientModInitializer
{
	public static final String MOD_ID = "areareplace";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private static final MinecraftClient client = MinecraftClient.getInstance();
	private static RegionReplacer replacer = new RegionReplacer();
	private static Sync sync = new Sync();
	
	@Override
	public void onInitializeClient() {
		registerClientCommands();
		registerClientDisconnect();
		registerClientTickEvent();
	}
	
	private void registerClientDisconnect()//断开连接后清理
	{
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			replacer.stop();
			sync.setSyncOffHand(-1);
		});
	}
	
	private void registerClientCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("areareplace")
							.then(ClientCommandManager.argument("from",CBlockPosArgumentType.blockPos())
							.then(ClientCommandManager.argument("to", CBlockPosArgumentType.blockPos())
							.then(ClientCommandManager.argument("sourceBlock", BlockStateArgumentType.blockState(registryAccess))
							.then(ClientCommandManager.literal("replace")
							.then(ClientCommandManager.argument("targetBlock", BlockStateArgumentType.blockState(registryAccess))
							.executes(context -> {
								ClientPlayerEntity player = client.player;
								if (player == null) return 0;
								
								// 获取方块坐标
								//BlockPos.ofFloored(this.toAbsolutePos(source));
								BlockPos fromPos = context.getArgument("from", CPosArgument.class).toAbsoluteBlockPos(context.getSource());
								BlockPos toPos = context.getArgument("to", CPosArgument.class).toAbsoluteBlockPos(context.getSource());
								
								// 获取方块类型（不关心具体状态）
								Block sourceBlock = context.getArgument("sourceBlock", BlockStateArgument.class)
										.getBlockState().getBlock();
								Block targetBlock = context.getArgument("targetBlock", BlockStateArgument.class)
										.getBlockState().getBlock();
								
								replacer.stop();
								replacer.setPositions(fromPos, toPos);
								replacer.setBlocks(sourceBlock, targetBlock);
								replacer.start();
								
								player.sendMessage(Text.literal(String.format(
										"开始替换任务：将区域(%d,%d,%d)到(%d,%d,%d)内的%s替换为%s",
										fromPos.getX(), fromPos.getY(), fromPos.getZ(), toPos.getX(), toPos.getY(), toPos.getZ(),
										targetBlock.getName().getString(),
										sourceBlock.getName().getString()
								)), false);
								
								return 1;
							}))))))
					.then(ClientCommandManager.literal("stop")
							.executes(context -> {
								ClientPlayerEntity player = client.player;
								if (player != null)
								{
									replacer.stop();
									player.sendMessage(Text.literal("已停止并取消替换任务"), false);
								}
								return 1;
							}))
					.then(ClientCommandManager.literal("restart")
							.executes(context -> {
								ClientPlayerEntity player = client.player;
								if (player != null)
								{
									replacer.start();//重新调用一次，只会清理选择的方块，完成重启
									player.sendMessage(Text.literal("已重启替换任务"), false);
								}
								return 1;
							}))
					.then(ClientCommandManager.literal("pause")
						.executes(context -> {
								ClientPlayerEntity player = client.player;
								if (player != null) {
									replacer.pause();
									player.sendMessage(Text.literal(replacer.isActive() ? "已恢复替换任务" : "已暂停替换任务"), false);
								}
								return 1;
							}))
					.then(ClientCommandManager.literal("syncoffhand")
					.then(ClientCommandManager.argument("tick", IntegerArgumentType.integer())
						.executes(context ->
							{
								ClientPlayerEntity player = client.player;
								if (player != null)
								{
									Integer tick = context.getArgument("tick", Integer.class);
									player.sendMessage(Text.literal(sync.setSyncOffHand(tick) ?
											(tick == -1 ? "已停止同步" : String.format("设置成功，将间隔每%dgt同步一次，使用-1停止同步", tick)) :
											"参数错误，需要大于等于0，或使用-1停止同步"), false);
								}
								return 1;
							})))
					.then(ClientCommandManager.literal("move")
					.then(ClientCommandManager.argument("direction",DirectionArgumentType.direction())
					.then(ClientCommandManager.argument("distance", IntegerArgumentType.integer())
						.executes(context ->
							{
								ClientPlayerEntity player = client.player;
								if(player != null)
								{
									DirectionArgumentType.Direction direction = context.getArgument("direction",DirectionArgumentType.Direction.class);
									Integer distance = context.getArgument("distance", Integer.class);
									replacer.moveSelect(direction, distance);
									player.sendMessage(Text.literal(String.format("将选区向：%s移动%d格，现在为从(%d,%d,%d)到(%d,%d,%d)",
											direction.toString(),
											distance,
											replacer.getPos1().getX(),replacer.getPos1().getY(),replacer.getPos1().getZ(),
											replacer.getPos2().getX(),replacer.getPos2().getY(),replacer.getPos2().getZ())));
								}
								return 1;
							}))))
					.then(ClientCommandManager.literal("swap")
						.executes(context ->
						{
							ClientPlayerEntity player = client.player;
							if(player != null)
							{
								replacer.swapSelect();
								player.sendMessage(Text.literal(String.format("已交换起始点与终点，现在为从(%d,%d,%d)到(%d,%d,%d)",
										replacer.getPos1().getX(),replacer.getPos1().getY(),replacer.getPos1().getZ(),
										replacer.getPos2().getX(),replacer.getPos2().getY(),replacer.getPos2().getZ())));
							}
							return 1;
						}))
					.then(ClientCommandManager.literal("range")
					.then(ClientCommandManager.argument("radius",DoubleArgumentType.doubleArg())
						.executes(context ->
						{
							ClientPlayerEntity player = client.player;
							if(player != null)
							{
								Double d = context.getArgument("radius",Double.class);
								player.sendMessage(Text.literal(replacer.setNearbyDistance(d) ?
										String.format("已设置目标方块操作范围为%f，默认值为4.0", d) : "设置失败，范围需要大于0且小等于20"));
							}
							return 1;
						})))
            );
		});
	}
	
	private void registerClientTickEvent()
	{
		ClientTickEvents.END_CLIENT_TICK.register(client ->
		{
			if (client != null)
			{
				sync.tick(client);
				replacer.tick(client);
			}
		});
	}
}

class Sync
{
	private boolean syncInv = false;
	private int syncTick = 0;
	private int syncRemTick = 0;
	
	public boolean setSyncOffHand(int tick)
	{
		if(tick == -1)
		{
			this.syncInv = false;
			this.syncTick = 0;
			this.syncRemTick = 0;
			return true;
		}
		
		if(tick < 0)
		{
			return false;
		}
		
		this.syncInv = true;
		this.syncTick = tick;
		this.syncRemTick = 0;
		return true;
	}
	
	private void syncOffHand(ClientPlayerEntity player)
	{
		//player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
		//player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(player.getInventory().getSlotWithStack(player.getOffHandStack())));
		if(player == null || player.networkHandler == null)
		{
			return;
		}
		// Create a unique item with NaN NBT to force desync detection
		ItemStack syncTrigger = new ItemStack(Items.STONE);
		syncTrigger.getOrCreateNbt().putDouble("offhand_sync", Double.NaN);
		
		// Send a fake interaction packet targeting the offhand slot (slot ID 40)
		player.networkHandler.sendPacket(new ClickSlotC2SPacket(
				player.currentScreenHandler.syncId,
				player.currentScreenHandler.getRevision(),
				(short)40,  // Offhand slot ID
				(byte)0,    // Button (unused for this operation)
				SlotActionType.QUICK_CRAFT,
				syncTrigger,
				new Int2ObjectOpenHashMap<>()
		));
		
		// The server will detect the NaN mismatch and send a full inventory sync
		// including the offhand slot back to the client
	}
	
	public void tick(MinecraftClient client)
	{
		if(!syncInv)
		{
			return;
		}
		
		if(syncRemTick <= 0)
		{
			syncOffHand(client.player);
			syncRemTick = syncTick;
		}
		else
		{
			--syncRemTick;
		}
	}
	
}



class RegionReplacer {
	private BlockPos pos1 = null;
	private BlockPos pos2 = null;
	private Block sourceBlock = null;
	private Block targetBlock = null;
	private boolean active = false;
	private BlockPos currentTarget = null;
	private BlockPos lastTarget = null;
	private double NEARBY_DISTANCE = 3.0;
	private boolean showChunkLoad = false;
	
	public BlockPos getPos1()
	{
		return pos1;
	}
	
	public boolean setNearbyDistance(double newDistance)
	{
		if (newDistance > 0.0 && newDistance <= 20.0)
		{
			NEARBY_DISTANCE = newDistance;
			return true;
		}
		
		return false;
	}
	
	public BlockPos getPos2()
	{
		return pos2;
	}
	
	public void setPositions(BlockPos pos1, BlockPos pos2) {
		this.pos1 = pos1;
		this.pos2 = pos2;
	}
	
	public void setBlocks(Block sourceBlock, Block targetBlock) {
		this.sourceBlock = targetBlock;//把目标替换为源所以此处相反
		this.targetBlock = sourceBlock;
	}
	
	public void swapSelect()
	{
		BlockPos temp = pos1;
		pos1 = pos2;
		pos2 = temp;
		
		//重置目标和上一个方块
		currentTarget = null;
		lastTarget = null;
	}
	
	public void moveSelect(DirectionArgumentType.Direction direction, int distance)
	{
		BlockPos movPos =
		switch (direction)
		{
			case UP -> new BlockPos(0,1,0);
			case DOWN -> new BlockPos(0,-1,0);
			
			case SOUTH -> new BlockPos(0,0,1);
			case NORTH -> new BlockPos(0,0,-1);
			
			case EAST -> new BlockPos(1,0,0);
			case WEST -> new BlockPos(-1,0,0);
		};
		
		//乘以移动距离，这样就能按照方向移动指定格子数，哪怕负数移动也能处理
		movPos = movPos.multiply(distance);
		
		pos1 = pos1.add(movPos);
		pos2 = pos2.add(movPos);
		
		//重置目标和上一个方块
		currentTarget = null;
		lastTarget = null;
	}
	
	public boolean isValid() {
		return pos1 != null && pos2 != null && sourceBlock != null && targetBlock != null;
	}
	
	private void clear()
	{
		pos1 = null;
		pos2 = null;
		sourceBlock = null;
		targetBlock = null;
		active = false;
		currentTarget = null;
		lastTarget = null;
		//NEARBY_DISTANCE = 3.0;
		showChunkLoad = false;
	}
	
	public void start()
	{
		if (isValid())
		{
			active = true;
			currentTarget = null;
			lastTarget = null;
		}
	}
	
	public void stop() {
		clear();
	}
	
	public void pause() {
		active = !active;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public boolean isPlayerNearby(ClientPlayerEntity player, @NotNull BlockPos currentTarget)
	{
		Box region = new Box(currentTarget, currentTarget);
		return player.getBoundingBox().expand(NEARBY_DISTANCE).intersects(region);
	}
	
	public void tick(MinecraftClient client)
	{
		ClientPlayerEntity player = client.player;
		if (player == null || !active || !isValid()) return;
		
		World world = player.getWorld();
		
		if (currentTarget == null)
		{
			currentTarget = findNextTarget(player, world);
			if (currentTarget == null)
			{
				return;
			}
		}
		
		if(!isPlayerNearby(player, currentTarget))
		{
			return;
		}
		
		if (tryBreakBlock(player, client) && tryPlaceBlock(player, client))//先尝试挖掘然后尝试放置
		{
			lastTarget = currentTarget;
			currentTarget = null;//放置成功则清空
		}
	}
	
	private boolean isBreakBlock(World world, BlockPos pos)
	{
		BlockState state = world.getBlockState(pos);
		return state.isOf(sourceBlock);
	}
	
	private BlockPos findNextTarget(ClientPlayerEntity player, World world)
	{
		//根据玩家指定顺序挖掘重放
		BlockPos mov = new BlockPos
		(
			pos1.getX() > pos2.getX() ? -1 : 1,
			pos1.getY() > pos2.getY() ? -1 : 1,
			pos1.getZ() > pos2.getZ() ? -1 : 1
		);//计算每个方块的位移量
		
		BlockPos beg = lastTarget == null
				? new BlockPos(pos1) //如果这是第一个方块，从头计算
				: new BlockPos(lastTarget);//否则从上一个方块开始计算，而不是从头
		
		//end作为尾后迭代器，需要根据位移移动一次
		BlockPos end = pos2.add(mov);
		
		boolean changeX = false;
		boolean changeZ = false;
		boolean changeY = false;
		
		//查找下一个方块
		while(true)
		{
			for (int y = beg.getY(); y != end.getY(); y += mov.getY())
			{
				for (int x = beg.getX(); x != end.getX(); x += mov.getX())
				{
					for (int z = beg.getZ(); z != end.getZ(); z += mov.getZ())
					{
						BlockPos pos = new BlockPos(x, y, z);
						ChunkPos chunkPos = new ChunkPos(pos);
						if(world.getChunkManager().getChunk(chunkPos.x,chunkPos.z) == null)//判断是否已加载区块，否直接返回null
						{
							if(!showChunkLoad)//防止一直提示
							{
								player.sendMessage(Text.of(String.format("目标方块未加载(%d,%d,%d)",x,y,z)), true);
								showChunkLoad = true;//已经显示过，下次不要在显示了
							}
							return null;
						}
						showChunkLoad = false;//下次可以显示了
						
						if (isBreakBlock(world, pos))
						{
							player.sendMessage(Text.of(String.format("当前选择的方块为(%d,%d,%d)",x,y,z)), true);
							return pos;
						}
					}
					if(!changeZ && lastTarget != null)
					{//如果这里Z到底了，并且beg是lastTarget来的，说明要从下一排开始了，重置beg的Z为pos1的Z
						beg = new BlockPos(beg.getX(),beg.getY(),pos1.getZ());
						changeZ = true;
					}
				}
				if(!changeX && lastTarget != null)
				{
					//同理，这里X到底了，并且beg是lastTarget来的，说明要从下一排开始了，重置beg的X为pos1的X
					beg = new BlockPos(pos1.getX(),beg.getY(),beg.getZ());
					changeX = true;
				}
			}
			if(!changeY && lastTarget != null)
			{
				//同理，这里Y到底了，并且beg是lastTarget来的，说明要从头开始了，重置beg的X为pos1的Y
				beg = new BlockPos(beg.getX(),pos1.getY(),beg.getZ());
				changeY = true;
				continue;//没有goto只能这样替代了
			}
			
			break;//正常只执行一次
		}
		
		return null;
	}
	
	private boolean canBreakBlock(BlockState targetState)
	{
		return  !targetState.isAir() &&
				!targetState.isOf(Blocks.AIR) &&
				!targetState.isOf(Blocks.CAVE_AIR) &&
				!targetState.isOf(Blocks.VOID_AIR) &&
				!(targetState.getBlock().getHardness() == -1) &&
				!(targetState.getBlock() instanceof FluidBlock);
	}
	
	private boolean canPlaceBlock(BlockState targetState)
	{
		return  targetState.isAir() || //必须要是空气或者流体或者可以替换的方块才能放置，否则失败循环让其重新挖掘
				targetState.isOf(Blocks.AIR) ||
				targetState.isOf(Blocks.CAVE_AIR) ||
				targetState.isOf(Blocks.VOID_AIR) ||
				targetState.isReplaceable() ||
				targetState.getBlock() instanceof FluidBlock;
	}
	
	private boolean tryBreakBlock(ClientPlayerEntity player, MinecraftClient client)
	{
		BlockState currentState = player.getWorld().getBlockState(currentTarget);
		
		if(currentState.isOf(targetBlock))//已经是目标方块则跳过
		{
			return true;
		}
		
		if(!canBreakBlock(currentState))
		{
			return true;//如果方块不能或无法破坏则成功
		}
		
		if(player.isBlockBreakingRestricted(player.getWorld(), currentTarget, client.interactionManager.getCurrentGameMode()))
		{
			//如果玩家当前没法破坏则失败，继续循环直到成功
			return false;
		}
		
		//否则执行破坏，防止死循环
		//client.interactionManager.breakBlock();
		client.interactionManager.updateBlockBreakingProgress(currentTarget, Direction.DOWN);
		client.interactionManager.cancelBlockBreaking();
		return !player.getWorld().getBlockState(currentTarget).isOf(sourceBlock);//如果还是需要挖掘的方块则失败
		
		//ClientPlayNetworkHandler networkHandler = player.networkHandler;
		//if (networkHandler != null)
		//{
		//	networkHandler.sendPacket(new PlayerActionC2SPacket(
		//			PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
		//			currentTarget,
		//			Direction.UP
		//	));
		//	networkHandler.sendPacket(new PlayerActionC2SPacket(
		//			PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
		//			currentTarget,
		//			Direction.UP
		//	));
		//	return !player.getWorld().getBlockState(currentTarget).isOf(sourceBlock);//如果还是需要挖掘的方块则失败
		//}
		//else
		//{
		//  return false;
		//}
		
	}
	
	private boolean tryPlaceBlock(ClientPlayerEntity player, MinecraftClient client) {
		ItemStack offhandStack = player.getStackInHand(Hand.OFF_HAND);//检查副手
		if (offhandStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() != targetBlock)
		{
			player.sendMessage(Text.literal(
					String.format("请在副手持有正确的方块：%s", targetBlock.getName().getString())
			), true);
			return false;
		}
		
		ClientPlayNetworkHandler networkHandler = player.networkHandler;
		BlockState currentState = player.getWorld().getBlockState(currentTarget);
		
		if(currentState.isOf(targetBlock))//已经是目标方块则跳过
		{
			return true;
		}
		
		if(!canPlaceBlock(currentState))//如果目标不能放置方块，则失败
		{
			return false;
		}
		
		client.interactionManager.interactBlock(player, Hand.OFF_HAND, new BlockHitResult(Vec3d.ofCenter(currentTarget), Direction.DOWN, currentTarget, true));
		return player.getWorld().getBlockState(currentTarget).isOf(targetBlock);//是目标方块则放置成功
		
		//if (networkHandler != null)
		//{
		//	BlockHitResult hitResult = new BlockHitResult(
		//			Vec3d.ofCenter(currentTarget),
		//			Direction.UP,
		//			currentTarget,
		//			false
		//	);
		//
		//	networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
		//			Hand.OFF_HAND,
		//			hitResult,
		//			0
		//	));
		//
		//	return player.getWorld().getBlockState(currentTarget).isOf(targetBlock);//是目标方块则放置成功
		//}
		//else
		//{
		//	return false;
		//}
	}
}