package net.hollowcube.common.util;

import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProtocolVersions {
    public static final int V1_21_4 = 769;
    public static final int V1_21_5 = 770;
    public static final int V1_21_6 = 771;
    public static final int V1_21_7 = 772;
    public static final int V1_21_9 = 773;
    public static final int V1_21_11 = 774;
    public static final int V26_1 = 775;
    public static final int V26_2 = 776;

    public static final int UNKNOWN = -1;
    public static final int MIN_SUPPORTED = V1_21_7;
    public static final int CURRENT = MinecraftServer.PROTOCOL_VERSION;
    private static final Logger logger = LoggerFactory.getLogger(ProtocolVersions.class);

    public static int getProtocolVersion(@NotNull String name) {
        return ID_TO_NAME.getOrDefault(name, -1);
    }

    public static @NotNull String getProtocolName(int version) {
        return NAME_TO_ID.getOrDefault(version, "unknown");
    }

    private static final Map<String, Integer> ID_TO_NAME = Map.ofEntries(
        Map.entry("1.21.4", V1_21_4),
        Map.entry("1.21.5", V1_21_5),
        Map.entry("1.21.6", V1_21_6),
        Map.entry("1.21.7", V1_21_7),
        Map.entry("1.21.9", V1_21_9),
        Map.entry("1.21.11", V1_21_11),
        Map.entry("26.1", V26_1),
        Map.entry("26.2", V26_2)
    );
    private static final Map<Integer, String> NAME_TO_ID = ID_TO_NAME.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    public static final List<String> SUPPORTED_PROTOCOL_NAMES = ID_TO_NAME.keySet().stream().sorted().toList();
}
