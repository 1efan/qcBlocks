package com.akpew.minecraft.qcblock.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QcbCommand {

    private QcbCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qcb")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("script", StringArgumentType.greedyString())
                .executes(QcbCommand::execute)));
    }

    // ── Data holders ──────────────────────────────────────────────

    private enum BlockKind { REPEAT, CHAIN, IMPULSE }

    private static class ParsedStatement {
        final BlockKind kind;
        final boolean conditional;
        final boolean needsRedstone;
        final String command;

        ParsedStatement(BlockKind kind, boolean conditional, boolean needsRedstone, String command) {
            this.kind = kind;
            this.conditional = conditional;
            this.needsRedstone = needsRedstone;
            this.command = command;
        }
    }

    // ── Execute ───────────────────────────────────────────────────

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§cqcb: only players can run this command."));
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "script");
        ServerLevel world = source.getLevel();

        try {
            // ── 1. Parse header ──────────────────────────────
            HeaderResult header = parseHeader(raw, player);

            // ── 2. Parse statements ─────────────────────────
            List<ParsedStatement> statements = parseStatements(header.remainingScript);
            if (statements.isEmpty()) {
                source.sendFailure(Component.literal("§cqcb: no commands found in the script."));
                return 0;
            }

            // ── 3. Calculate positions ──────────────────────
            List<BlockPos> positions = computePositions(player, header, statements.size());

            // ── 4. Place command blocks ─────────────────────
            placeBlocks(world, positions, statements, header.chainDirection);

            source.sendSuccess(() -> Component.literal(String.format(
                "§aqcb: placed §f%d §acommand block%s §7[start %s, chain %s]",
                statements.size(),
                statements.size() == 1 ? "" : "s",
                positions.get(0).toShortString(),
                header.chainDirection.getName()
            )), false);

            return statements.size();

        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§cqcb: " + e.getMessage()));
            return 0;
        }
    }

    // ── Header parsing ────────────────────────────────────────────

    private static class HeaderResult {
        Direction chainDirection = Direction.NORTH;
        final Map<String, Integer> offsets = new HashMap<>();
        String remainingScript = "";
    }

    private static HeaderResult parseHeader(String raw, ServerPlayer player) {
        HeaderResult result = new HeaderResult();

        // Defaults
        result.chainDirection = relativeDirection(player, "forward");
        result.offsets.put("forward", 1);

        String working = raw.trim();

        // Find where the header ends and statements begin
        int firstStmt = findFirstStatementStart(working);
        String headerStr;
        String body;
        if (firstStmt >= 0) {
            headerStr = working.substring(0, firstStmt).trim();
            body = working.substring(firstStmt).trim();
        } else {
            headerStr = working;
            body = "";
        }

        boolean dirExplicitlySet = false;

        if (!headerStr.isEmpty()) {
            String[] tokens = headerStr.split("[\\s;,]+");
            for (String token : tokens) {
                if (token.isEmpty()) continue;
                String[] kv = token.split("=", 2);
                String key = kv[0].toLowerCase().trim();

                if (key.equals("dir")) {
                    if (kv.length < 2)
                        throw new IllegalArgumentException("dir= requires a direction value.");
                    String dirVal = kv[1].toLowerCase().trim();
                    result.chainDirection = resolveDirection(dirVal, player);
                    dirExplicitlySet = true;
                } else {
                    int amount = 1;
                    if (kv.length >= 2) {
                        try {
                            amount = Integer.parseInt(kv[1].trim());
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                "invalid offset value for '" + key + "': " + kv[1].trim());
                        }
                    }
                    result.offsets.put(key, amount);
                }
            }
        }

        // If dir wasn't explicitly set, infer it from a directional offset
        if (!dirExplicitlySet) {
            for (String d : new String[]{"forward", "back", "left", "right",
                                          "north", "south", "east", "west"}) {
                if (result.offsets.containsKey(d) && result.offsets.get(d) > 0) {
                    result.chainDirection = resolveDirection(d, player);
                    break;
                }
            }
        }

        result.remainingScript = body;
        return result;
    }

    private static int findFirstStatementStart(String script) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:^|[;\\s\\n\\r])([rci])(?:\\[|:)");
        java.util.regex.Matcher m = p.matcher(script);
        if (m.find()) {
            return m.start(1);
        }
        return -1;
    }

    // ── Statement parsing ─────────────────────────────────────────

    private static List<ParsedStatement> parseStatements(String body) {
        List<ParsedStatement> list = new ArrayList<>();
        if (body == null || body.isEmpty()) return list;

        String[] parts = body.split("[;\\n\\r]+");
        boolean isFirst = true;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            ParsedStatement stmt = parseOneStatement(trimmed, isFirst);
            if (stmt != null) {
                list.add(stmt);
                isFirst = false;
            }
        }

        return list;
    }

    private static ParsedStatement parseOneStatement(String text, boolean isFirst) {
        java.util.regex.Pattern prefixed = java.util.regex.Pattern.compile(
            "^([rci])(?:\\[([^\\]]*)\\])?:\\s*(.+)$", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = prefixed.matcher(text);

        if (m.matches()) {
            String kindChar = m.group(1);
            String flagsStr = m.group(2);
            String command = m.group(3).trim();

            BlockKind kind = switch (kindChar) {
                case "r" -> BlockKind.REPEAT;
                case "c" -> BlockKind.CHAIN;
                case "i" -> BlockKind.IMPULSE;
                default -> BlockKind.CHAIN;
            };

            boolean conditional = false;
            boolean needsRedstone = false;

            if (flagsStr != null) {
                for (String flag : flagsStr.split(",")) {
                    flag = flag.trim().toLowerCase();
                    switch (flag) {
                        case "cond", "conditional" -> conditional = true;
                        case "needs", "needsredstone", "redstone" -> needsRedstone = true;
                        case "auto", "always" -> { /* explicitly always active — default */ }
                        default -> throw new IllegalArgumentException(
                            "unknown flag '" + flag + "' (valid: cond, needs, auto)");
                    }
                }
            }

            // Impulse defaults to needs-redstone; repeat/chain default to always-active
            if (kind == BlockKind.IMPULSE && flagsStr == null) {
                needsRedstone = true;
            }

            return new ParsedStatement(kind, conditional, needsRedstone, command);
        }

        // No prefix — bare command.  First → repeat, rest → chain (all always-active).
        BlockKind kind = isFirst ? BlockKind.REPEAT : BlockKind.CHAIN;
        return new ParsedStatement(kind, false, false, text.trim());
    }

    // ── Position calculation ──────────────────────────────────────

    private static List<BlockPos> computePositions(ServerPlayer player,
                                                    HeaderResult header, int count) {
        List<BlockPos> positions = new ArrayList<>();

        BlockPos playerPos = player.blockPosition();
        int x = playerPos.getX();
        int y = playerPos.getY();
        int z = playerPos.getZ();

        // Apply all offsets
        for (Map.Entry<String, Integer> entry : header.offsets.entrySet()) {
            Direction dir = resolveDirection(entry.getKey(), player);
            int amount = entry.getValue();
            x += dir.getStepX() * amount;
            y += dir.getStepY() * amount;
            z += dir.getStepZ() * amount;
        }

        Direction chainDir = header.chainDirection;

        for (int i = 0; i < count; i++) {
            positions.add(new BlockPos(
                x + chainDir.getStepX() * i,
                y + chainDir.getStepY() * i,
                z + chainDir.getStepZ() * i
            ));
        }

        return positions;
    }

    // ── Block placement ───────────────────────────────────────────

    private static void placeBlocks(ServerLevel world, List<BlockPos> positions,
                                     List<ParsedStatement> statements, Direction facing) {
        for (int i = 0; i < statements.size(); i++) {
            BlockPos pos = positions.get(i);
            ParsedStatement stmt = statements.get(i);

            // Clear existing block
            BlockState existing = world.getBlockState(pos);
            if (!existing.isAir()) {
                world.destroyBlock(pos, true);
            }

            // Pick the right block
            Block block = switch (stmt.kind) {
                case REPEAT -> Blocks.REPEATING_COMMAND_BLOCK;
                case CHAIN -> Blocks.CHAIN_COMMAND_BLOCK;
                case IMPULSE -> Blocks.COMMAND_BLOCK;
            };

            BlockState state = block.defaultBlockState()
                .setValue(DirectionalBlock.FACING, facing)
                .setValue(CommandBlock.CONDITIONAL, stmt.conditional);

            world.setBlock(pos, state, Block.UPDATE_CLIENTS);

            // Configure the block entity
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CommandBlockEntity cmdBe) {
                cmdBe.getCommandBlock().setCommand(stmt.command);
                cmdBe.getCommandBlock().setTrackOutput(false);
                cmdBe.setAutomatic(!stmt.needsRedstone);
                cmdBe.setChanged();
                world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ── Direction helpers ─────────────────────────────────────────

    private static Direction resolveDirection(String name, ServerPlayer player) {
        return switch (name.toLowerCase()) {
            case "forward", "forwards", "f" -> relativeDirection(player, "forward");
            case "back", "backwards", "b"    -> relativeDirection(player, "back");
            case "left", "l"                 -> relativeDirection(player, "left");
            case "right", "r"                -> relativeDirection(player, "right");
            case "up", "u"                   -> Direction.UP;
            case "down", "d"                 -> Direction.DOWN;
            case "north", "n"                -> Direction.NORTH;
            case "south", "s"                -> Direction.SOUTH;
            case "east", "e"                 -> Direction.EAST;
            case "west", "w"                 -> Direction.WEST;
            default -> throw new IllegalArgumentException(
                "unknown direction '" + name +
                "'. Valid: forward, back, left, right, up, down, north, south, east, west");
        };
    }

    private static Direction relativeDirection(ServerPlayer player, String relative) {
        // Minecraft yaw: 0=south, 90=west, 180=north, 270=east
        float yaw = (player.getYRot() % 360 + 360) % 360;

        Direction facing;
        if (yaw >= 45 && yaw < 135) {
            facing = Direction.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            facing = Direction.NORTH;
        } else if (yaw >= 225 && yaw < 315) {
            facing = Direction.EAST;
        } else {
            facing = Direction.SOUTH;
        }

        return switch (relative.toLowerCase()) {
            case "forward" -> facing;
            case "back"    -> facing.getOpposite();
            case "left"    -> facing.getCounterClockWise();
            case "right"   -> facing.getClockWise();
            default        -> facing;
        };
    }
}
