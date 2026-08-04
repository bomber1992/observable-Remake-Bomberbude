package observable.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.Locale;

/** Small reflection boundary for optional AE2 owner/part metadata. */
public final class Ae2ProfilerSupport {
    private Ae2ProfilerSupport() {
    }

    public static BlockPos findBlockPos(Object owner) {
        if (owner == null) {
            return null;
        }
        if (owner instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockPos();
        }
        if (owner instanceof Entity entity) {
            return entity.blockPosition();
        }

        Object blockEntity = invokeNoArg(owner, "getBlockEntity");
        if (blockEntity instanceof BlockEntity be) {
            return be.getBlockPos();
        }

        Object host = invokeNoArg(owner, "getHost");
        if (host != null && host != owner) {
            BlockPos hostPos = findBlockPos(host);
            if (hostPos != null) {
                return hostPos;
            }
        }

        Object location = invokeNoArg(owner, "getLocation");
        BlockPos locationPos = extractBlockPos(location);
        if (locationPos != null) {
            return locationPos;
        }

        return extractBlockPos(owner);
    }

    public static String findDisplayName(Object owner) {
        if (owner == null) {
            return "ae2:unknown [AE2 grid tick]";
        }

        String id = findRegistryId(owner);
        String side = findSide(owner);
        StringBuilder result = new StringBuilder(id).append(" [AE2 grid tick");
        if (side != null && !side.isBlank()) {
            result.append(", ").append(side);
        }
        return result.append(']').toString();
    }

    private static String findRegistryId(Object owner) {
        Object partItem = invokeNoArg(owner, "getPartItem");
        if (partItem instanceof ItemLike itemLike) {
            var key = BuiltInRegistries.ITEM.getKey(itemLike.asItem());
            if (key != null) {
                return key.toString();
            }
        }

        if (owner instanceof BlockEntity blockEntity) {
            var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            if (key != null) {
                return key.toString();
            }
        }

        String simpleName = owner.getClass().getSimpleName();
        if (simpleName.isBlank()) {
            simpleName = owner.getClass().getName();
        }
        return "ae2:" + toSnakeCase(simpleName);
    }

    private static String findSide(Object owner) {
        Object side = invokeNoArg(owner, "getSide");
        if (side == null) {
            side = invokeNoArg(owner, "getDirection");
        }
        return side == null ? null : side.toString().toLowerCase(Locale.ROOT);
    }

    private static BlockPos extractBlockPos(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BlockPos pos) {
            return pos;
        }
        for (String methodName : new String[] {"getBlockPos", "getPos", "pos"}) {
            Object result = invokeNoArg(value, methodName);
            if (result instanceof BlockPos pos) {
                return pos;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String toSnakeCase(String name) {
        return name
                .replaceAll("(Part|BlockEntity)$", "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .toLowerCase(Locale.ROOT);
    }
}
