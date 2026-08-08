package net.hollowcube.terraform.selection.region;

import net.hollowcube.common.util.NetworkBufferTypes;
import net.hollowcube.terraform.util.math.CoordinateUtil;
import net.minestom.server.coordinate.Point;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class LineRegionSelector implements RegionSelector {
    private final String selectionId;

    private Point pos1 = null;
    private Point pos2 = null;

    public LineRegionSelector(@NotNull String selectionId) {
        this.selectionId = selectionId;
    }

    @Override
    public boolean selectPrimary(@NotNull Point point, boolean explain) {
        if (pos1 != null && point.sameBlock(pos1)) return false;
        pos1 = CoordinateUtil.floor(point);

        return true;
    }

    @Override
    public boolean selectSecondary(@NotNull Point point, boolean explain) {
        if (pos2 != null && point.sameBlock(pos2)) return false;
        pos2 = CoordinateUtil.floor(point);

        return true;
    }

    @Override
    public void clear() {
        pos1 = null;
        pos2 = null;
    }

    @Override
    public @Nullable Region region() {
        if (pos1 == null || pos2 == null) return null;
        return new LineRegion(pos1, pos2);
    }

    @Override
    public void write(@NotNull NetworkBuffer buffer) {
        buffer.write(NetworkBufferTypes.OPT_VECTOR3, pos1);
        buffer.write(NetworkBufferTypes.OPT_VECTOR3, pos2);
    }

    @Override
    public void read(@NotNull NetworkBuffer buffer) {
        pos1 = buffer.read(NetworkBufferTypes.OPT_VECTOR3);
        pos2 = buffer.read(NetworkBufferTypes.OPT_VECTOR3);
    }

}
