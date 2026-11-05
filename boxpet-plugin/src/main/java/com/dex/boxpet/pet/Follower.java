package com.dex.boxpet.pet;

import com.dex.boxpet.config.Settings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Movement of active pets, one call per pet per tick (spec 4.1, 4.2, 4.5). Pure positioning: the manager handles
 * models, name tags, chat and the side effects it is told about.
 */
public final class Follower {

    private final PetManager manager;

    public Follower(PetManager manager) {
        this.manager = manager;
    }

    private Settings s() {
        return manager.plugin().settings();
    }

    public void tick(ActivePet p) {
        Player o = p.owner;
        if (!o.isOnline() || o.isDead() || p.visual == null || p.deadHidden || p.teleporting) return;
        Location ol = o.getLocation();
        if (p.pos.getWorld() != ol.getWorld()) {
            manager.reposition(p, true);
            return;
        }
        trackOwner(p, ol);

        // 4.5 fast travel
        if (fastTravel(p, o)) return;

        boolean combat = manager.combat().tagged(o.getUniqueId());
        if (combat) p.combatTicksLeft = s().combatEndTicks;
        else if (p.combatTicksLeft > 0) p.combatTicksLeft--;
        boolean inCombat = combat || p.combatTicksLeft > 0;

        // 4.2 carried
        if (p.carried) {
            if (inCombat || o.isGliding() || o.isInsideVehicle() || o.isSwimming() || o.isInWater() || o.isRiptiding()) {
                manager.putDown(p, inCombat ? "combat" : "move");
            } else {
                carry(p, o, ol);
                return;
            }
        }

        if (p.sitting && (inCombat || ol.distanceSquared(p.anchor == null ? p.pos : p.anchor) > sq(s().sitMaxDistance))) {
            manager.stand(p, true);
        }

        if (p.visual != null) p.visual.hitbox(!inCombat);

        Location target;
        double maxSpeed = p.type.fly ? s().flyMaxSpeed : s().maxSpeed;
        float faceYaw;
        if (inCombat) {
            p.mode = ActivePet.Mode.COMBAT;
            p.followingToSlot = false;
            p.wanderTarget = null;
            p.restTicks = 0;
            target = slot(p, ol, ol.getYaw());
            target = avoidView(p, o, target);
            faceYaw = ol.getYaw();
        } else if (p.sitting) {
            p.mode = ActivePet.Mode.SIT;
            target = p.anchor == null ? p.pos : p.anchor;
            faceYaw = yawTo(p.pos, ol);
        } else {
            p.mode = p.moving ? ActivePet.Mode.FOLLOW : ActivePet.Mode.REST;
            double distOwner = horizontal(p.pos, ol);
            if (!p.followingToSlot && (distOwner > s().startDistance || Math.abs(p.pos.getY() - ol.getY()) > 3)) {
                p.followingToSlot = true;
                p.wanderTarget = null;
            }
            if (p.followingToSlot) {
                target = slot(p, ol, p.moving ? p.moveYaw : p.moveYaw);
                if (p.pos.distanceSquared(target) < 0.09) {
                    p.followingToSlot = false;
                    p.anchor = p.pos.clone();
                    p.restTicks = 0;
                }
                faceYaw = p.moving ? p.moveYaw : yawTo(p.pos, ol);
            } else if (p.moving) {
                target = p.pos;
                faceYaw = yawTo(p.pos, ol);
            } else {
                target = rest(p, o, ol);
                faceYaw = p.wanderTarget != null ? yawTo(p.pos, p.wanderTarget) : yawTo(p.pos, ol);
            }
            if (p.risen) p.risen = false;
        }

        double step = moveTowards(p, target, inCombat ? maxSpeed * 1.4 : maxSpeed);
        if (p.faceYaw != null && p.faceTicks > 0) {
            faceYaw = p.faceYaw;
            if (--p.faceTicks <= 0) p.faceYaw = null;
        } else if (step > 0.03 && !inCombat && !p.sitting) {
            faceYaw = yawOf(p.pos, target, faceYaw);
        }
        p.yaw = lerpYaw(p.yaw, faceYaw, 0.35f);
        p.visual.move(p.pos, p.yaw, p.yaw);
        p.visual.walking(step > 0.02);
        manager.afterMove(p);
    }

    /** Where a pet appears next to its owner (summon, teleport, respawn): the back-right slot of the camera. */
    public Location spawnSlot(ActivePet p) {
        Location ol = p.owner.getLocation();
        Location at = slot(p, ol, ol.getYaw());
        at.setYaw(ol.getYaw());
        return at;
    }

    // ------------------------------------------------------------------ owner tracking

    private void trackOwner(ActivePet p, Location ol) {
        if (p.lastOwner != null && p.lastOwner.getWorld() == ol.getWorld()) {
            double dx = ol.getX() - p.lastOwner.getX();
            double dy = ol.getY() - p.lastOwner.getY();
            double dz = ol.getZ() - p.lastOwner.getZ();
            double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
            p.ownerSpeed = p.ownerSpeed * 0.6 + d3 * 0.4;
            if (dx * dx + dz * dz > 0.0009) p.moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            p.moving = p.ownerSpeed > 0.05;
        }
        p.lastOwner = ol.clone();
    }

    /** @return true when the pet is hidden for fast travel this tick (nothing else to do). */
    private boolean fastTravel(ActivePet p, Player o) {
        boolean special = o.isGliding() || o.isInsideVehicle() || o.isRiptiding();
        boolean fast = special || p.ownerSpeed > s().fastTravelSpeed;
        if (fast) {
            p.fastTicks++;
            p.slowTicks = 0;
        } else {
            p.slowTicks++;
            p.fastTicks = 0;
        }
        if (!p.travelHidden) {
            boolean hide;
            if (p.type.fly) {
                hide = p.ownerSpeed > s().flyMaxSpeed && p.fastTicks >= s().fastTravelSustainTicks
                        || o.isInsideVehicle() && p.fastTicks >= 5;
            } else {
                hide = special && p.fastTicks >= 5 || p.ownerSpeed > s().fastTravelSpeed && p.fastTicks >= s().fastTravelSustainTicks;
            }
            if (hide) {
                manager.hideForTravel(p);
                return true;
            }
            return false;
        }
        boolean landed = !special && p.slowTicks >= s().fastTravelLandTicks;
        if (landed) manager.showAfterTravel(p);
        return !landed;
    }

    // ------------------------------------------------------------------ carry

    private void carry(ActivePet p, Player o, Location ol) {
        p.mode = ActivePet.Mode.CARRIED;
        float body = o.getBodyYaw();
        Vector fwd = forward(body);
        Location at = ol.clone().add(fwd.multiply(s().carryForward)).add(0, s().carryUp, 0);
        at.setYaw(body);
        p.pos = at;
        p.yaw = body;
        if (p.visual != null) {
            p.visual.hitbox(false);
            p.visual.move(at, body, body);
            p.visual.walking(false);
        }
        manager.afterMove(p);
    }

    // ------------------------------------------------------------------ rest

    private Location rest(ActivePet p, Player o, Location ol) {
        if (p.anchor == null) p.anchor = p.pos.clone();
        p.restTicks++;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // looked at by the owner
        Location eye = o.getEyeLocation();
        Vector toPet = p.pos.clone().add(0, p.visual == null ? 0.5 : p.visual.height() * 0.5, 0).toVector().subtract(eye.toVector());
        double len = toPet.length();
        if (len < 8 && len > 0.01 && Math.toDegrees(toPet.angle(eye.getDirection())) < 12) {
            if (++p.lookTicks >= s().lookReactTicks) {
                p.lookTicks = 0;
                long now = System.currentTimeMillis();
                if (now - p.lastLookReact > s().lookReactCooldownSeconds * 1000L) {
                    p.lastLookReact = now;
                    manager.playAnimation(p, "interact");
                    manager.plugin().chat().say(p, "looked");
                }
            }
        } else {
            p.lookTicks = 0;
        }
        if (--p.nextInteract <= 0) {
            p.nextInteract = 400 + r.nextInt(400);
            if (p.restTicks > 60 && p.wanderTarget == null) manager.playAnimation(p, "interact");
        }
        if (p.wanderTarget != null) {
            if (p.pos.distanceSquared(p.wanderTarget) < 0.04) p.wanderTarget = null;
            else return p.wanderTarget;
        }
        if (p.restTicks > s().wanderAfterTicks && s().wanderRadius > 0 && --p.nextWander <= 0) {
            p.nextWander = 60 + r.nextInt(80);
            double a = r.nextDouble() * Math.PI * 2;
            double d = 0.5 + r.nextDouble() * s().wanderRadius;
            Location w = p.anchor.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            w.setY(height(p, w, ol));
            if (passable(w)) {
                p.wanderTarget = w;
                return w;
            }
        }
        return p.pos;
    }

    // ------------------------------------------------------------------ slots

    /** Back-right slot relative to {@code yaw} (spec 4), on the ground or at fly height, never inside a block. */
    private Location slot(ActivePet p, Location ol, float yaw) {
        double side = p.type.side != null ? p.type.side : s().followSide;
        double back = p.type.back != null ? p.type.back : s().followBack;
        Vector right = new Vector(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw)));
        Vector fwd = forward(yaw);
        Location slot = ol.clone().add(right.multiply(side)).subtract(fwd.multiply(back));
        slot.setY(height(p, slot, ol));
        if (!passable(slot)) {
            for (int i = 1; i <= 4; i++) {
                double f = 1 - i * 0.25;
                Location closer = ol.clone().add(new Vector(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw)))
                        .multiply(side * f)).subtract(forward(yaw).multiply(back * f));
                closer.setY(height(p, closer, ol));
                if (passable(closer)) return closer;
            }
            Location own = ol.clone();
            own.setY(ol.getY() + (p.type.fly ? 1.4 : 0));
            return own;
        }
        return slot;
    }

    /** Combat: out of the owner's view cone in front (rise above the head). */
    private Location avoidView(ActivePet p, Player o, Location target) {
        Location eye = o.getEyeLocation();
        Vector v = p.pos.toVector().subtract(eye.toVector());
        double len = v.length();
        boolean inView = len > 0.01 && len < s().combatNear && Math.toDegrees(v.angle(eye.getDirection())) < s().combatViewCone / 2;
        if (inView || p.risen) {
            p.risen = inView || p.pos.distanceSquared(target) > 1.0;
            Location up = target.clone();
            up.setY(eye.getY() + s().combatRise);
            return up;
        }
        return target;
    }

    private double height(ActivePet p, Location at, Location ol) {
        double extra = p.type.height != null ? p.type.height : s().followHeight;
        if (p.type.fly) return ol.getY() + s().flyHeight + extra;
        return groundY(at.getWorld(), at.getX(), ol.getY(), at.getZ()) + extra;
    }

    /** Top of the first solid block under (x, y + 1, z), up to 6 blocks down; in water: the owner's height. */
    static double groundY(World w, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return y;
        int start = (int) Math.floor(y + 1.0);
        Block feet = w.getBlockAt(bx, (int) Math.floor(y), bz);
        if (feet.isLiquid()) return y;
        for (int by = start; by >= start - 6 && by > w.getMinHeight(); by--) {
            Block b = w.getBlockAt(bx, by, bz);
            if (b.isLiquid()) return Math.max(y, by + 0.9);
            if (!b.isPassable()) {
                double top = b.getBoundingBox().getMaxY();
                return top > by ? top : by + 1;
            }
        }
        return y;
    }

    private static boolean passable(Location at) {
        World w = at.getWorld();
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
        Block feet = w.getBlockAt(bx, (int) Math.floor(at.getY() + 0.05), bz);
        Block head = feet.getRelative(0, 1, 0);
        return feet.isPassable() && head.isPassable();
    }

    // ------------------------------------------------------------------ movement

    /** Moves the pet towards the target; teleports when too far. @return distance moved this tick */
    private double moveTowards(ActivePet p, Location target, double maxSpeed) {
        if (target.getWorld() != p.pos.getWorld()) {
            manager.reposition(p, true);
            return 0;
        }
        double dist = p.pos.distance(target);
        if (dist > s().teleportDistance) {
            manager.teleportPet(p, target);
            return 0;
        }
        if (dist < 0.03) return 0;
        double speed = Math.min(maxSpeed, 0.08 + dist * 0.2);
        if (p.wanderTarget != null && target == p.wanderTarget) speed = Math.min(speed, 0.07);
        double step = Math.min(speed, dist);
        Vector dir = target.toVector().subtract(p.pos.toVector()).normalize().multiply(step);
        p.pos = p.pos.clone().add(dir);
        return step;
    }

    // ------------------------------------------------------------------ maths

    static Vector forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float yawOf(Location from, Location to, float fallback) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < 1e-4) return fallback;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    static float lerpYaw(float from, float to, float t) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        return from + d * t;
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double sq(double d) {
        return d * d;
    }
}
