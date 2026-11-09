package com.lordethern.dawnbreak.client;

import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.Galaxy;
import com.lordethern.dawnbreak.galaxy.StarSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** The navigation target set on the galaxy map: a planet/moon, or a whole system (its star). */
public final class Waypoint {
    private static ResourceLocation target;
    private static boolean isBody;

    private Waypoint() {}

    public static void setBody(Body body) {
        target = body.id();
        isBody = true;
    }

    public static void setSystem(StarSystem system) {
        target = system.id();
        isBody = false;
    }

    public static void clear() {
        target = null;
    }

    public static boolean isBody() {
        return isBody;
    }

    @Nullable
    public static ResourceLocation target() {
        return target;
    }

    public static Optional<Vec3> position(Galaxy galaxy, double time) {
        if (target == null) return Optional.empty();
        if (isBody) return galaxy.body(target).flatMap(body -> galaxy.positionOf(body, time));
        return galaxy.system(target).map(system -> system.starPosition(0, time));
    }

    public static Optional<Component> name(Galaxy galaxy) {
        if (target == null) return Optional.empty();
        if (isBody) return galaxy.body(target).map(Body::displayName);
        return galaxy.system(target).map(StarSystem::displayName);
    }
}
