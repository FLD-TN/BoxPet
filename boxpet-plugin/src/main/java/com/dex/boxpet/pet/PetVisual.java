package com.dex.boxpet.pet;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.ModelState;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.entity.Hitbox;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A ModelEngine R4 model on a packet-only {@link Dummy} (spec 4): no real entity, nothing to hit, nothing to get
 * stuck. Used for pets and for VFX models. Main thread only.
 */
public final class PetVisual {

    private static final Hitbox NO_HITBOX = new Hitbox(0.01, 0.01, 0.01, 0.0);

    private final Dummy<Object> dummy;
    private final ModeledEntity modeled;
    private ActiveModel model;
    private final String modelId;
    private double scale;
    private final double height;
    private Hitbox hitbox;
    private boolean hitboxOn = true;
    private boolean removed;

    private PetVisual(Dummy<Object> dummy, ModeledEntity modeled, ActiveModel model, String modelId, double scale) {
        this.dummy = dummy;
        this.modeled = modeled;
        this.model = model;
        this.modelId = modelId;
        this.scale = scale;
        double h = 1.0;
        try {
            Hitbox box = model.getBlueprint().getMainHitbox();
            if (box != null) {
                h = box.getHeight();
                hitbox = box;
            }
        } catch (RuntimeException ignored) {
            // models without a main hitbox
        }
        this.height = h;
    }

    public static boolean exists(String modelId) {
        return modelId != null && !modelId.isEmpty() && ModelEngineAPI.getBlueprint(modelId) != null;
    }

    /**
     * @param idle / walk animation names for the automatic idle / walk states; {@code null} keeps ModelEngine's
     * @return {@code null} when the model does not exist or ModelEngine refused it
     */
    /** 0.9.7: false = the idle animation plays once when the pet stops, then it holds that pose (config follow.idle-loop). */
    public static boolean idleLoop = false;
    /** No animation has this name: keeps ModelEngine from looping "idle" by itself. */
    private static final String NO_IDLE = "boxpet_no_idle";

    public static @Nullable PetVisual create(Location at, String modelId, double scale, int renderRadius,
                                             @Nullable String idle, @Nullable String walk, boolean withHitbox,
                                             Logger log) {
        if (!exists(modelId)) return null;
        try {
            Dummy<Object> dummy = new Dummy<>();
            dummy.setLocation(at);
            dummy.setRenderRadius(renderRadius);
            dummy.setYBodyRot(at.getYaw());
            dummy.setYHeadRot(at.getYaw());
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(dummy);
            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            if (model == null) {
                modeled.destroy();
                return null;
            }
            model.setScale(scale);
            model.setHitboxScale(scale);
            AnimationHandler handler = model.getAnimationHandler();
            if (idle != null && !idle.isEmpty()) {
                handler.setDefaultProperty(new AnimationHandler.DefaultProperty(ModelState.IDLE, idleLoop ? idle : NO_IDLE, 0.2, 0.2, 1.0));
            }
            if (walk != null && !walk.isEmpty()) {
                handler.setDefaultProperty(new AnimationHandler.DefaultProperty(ModelState.WALK, walk, 0.2, 0.2, 1.0));
            }
            modeled.addModel(model, withHitbox);
            if (!withHitbox) dummy.setHitbox(NO_HITBOX);
            modeled.setBaseEntityVisible(false);
            PetVisual v = new PetVisual(dummy, modeled, model, modelId, scale);
            if (!withHitbox) v.hitboxOn = false;
            if (!idleLoop && idle != null && !idle.isEmpty()) {
                v.idleOnce = idle;
                v.playIdleOnce();
            }
            return v;
        } catch (RuntimeException | LinkageError ex) {
            log.log(Level.WARNING, "ModelEngine could not create model " + modelId, ex);
            return null;
        }
    }

    public UUID baseId() {
        return dummy.getUUID();
    }

    public int entityId() {
        return dummy.getEntityId();
    }

    public Object base() {
        return dummy;
    }

    public String modelId() {
        return modelId;
    }

    /** Height of the model's main hitbox at the current scale (1 block when it has none). */
    public double height() {
        return height * scale;
    }

    public void move(Location at, float bodyYaw, float headYaw) {
        if (removed) return;
        dummy.setLocation(at);
        dummy.setYBodyRot(bodyYaw);
        dummy.setYHeadRot(headYaw);
    }

    public void walking(boolean walking) {
        if (removed || dummy.isWalking() == walking) return;
        dummy.setWalking(walking);
        if (idleOnce == null) return;
        // stopped: idle once and hold its last frame; moving again: drop it so the walk state shows
        if (walking) stop(idleOnce);
        else playIdleOnce();
    }

    /** Name of the idle animation when it is played once (null = ModelEngine loops it). */
    private @Nullable String idleOnce;

    private void playIdleOnce() {
        try {
            var prop = model.getAnimationHandler().playAnimation(idleOnce, 0.2, 0.2, 1.0, true);
            if (prop != null) prop.setForceLoopMode(com.ticxo.modelengine.api.animation.BlueprintAnimation.LoopMode.HOLD);
        } catch (RuntimeException ignored) {
            // animation missing in this model
        }
    }

    /** Plays a one-shot animation over the idle / walk state. Unknown names are ignored by ModelEngine. */
    public void play(String animation) {
        if (removed || animation == null || animation.isEmpty()) return;
        try {
            model.getAnimationHandler().playAnimation(animation, 0.1, 0.1, 1.0, true);
        } catch (RuntimeException ignored) {
            // animation missing in this model
        }
    }

    /** Hides these bones and everything under them (a pack effect this pet must not show). */
    public void hideBones(java.util.List<String> ids) {
        if (removed) return;
        for (String id : ids) {
            try {
                model.getBone(id).ifPresent(PetVisual::hideTree);
            } catch (RuntimeException ignored) {
                // no such bone in this model
            }
        }
    }

    private static void hideTree(com.ticxo.modelengine.api.model.bone.ModelBone bone) {
        bone.setVisible(false);
        for (var child : bone.getChildren().values()) hideTree(child);
    }

    public void stop(String animation) {
        if (removed || animation == null || animation.isEmpty()) return;
        try {
            model.getAnimationHandler().stopAnimation(animation);
        } catch (RuntimeException ignored) {
            // not playing
        }
    }

    public boolean hasAnimation(String animation) {
        try {
            return model.getBlueprint().getAnimations().containsKey(animation);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void scale(double newScale) {
        if (removed || Math.abs(newScale - scale) < 1e-4) return;
        scale = newScale;
        model.setScale(newScale);
        model.setHitboxScale(newScale);
    }

    /** Shown or hidden for everyone (dead owner, fast travel). */
    public void visible(boolean visible) {
        if (!removed && dummy.isVisible() != visible) dummy.setVisible(visible);
    }

    /** Hidden for one viewer only (hide other players' pets, visible cap). */
    public void hiddenFor(Player viewer, boolean hidden) {
        if (removed) return;
        dummy.setForceHidden(viewer, hidden);
    }

    /** Owner clicks only work with the hitbox on; off in combat (spec 4.4). */
    public void hitbox(boolean on) {
        if (removed || hitboxOn == on) return;
        hitboxOn = on;
        dummy.setHitbox(on && hitbox != null ? hitbox : NO_HITBOX);
        model.setHitboxScale(on ? scale : 0.01);
    }

    public boolean removed() {
        return removed;
    }

    public void remove() {
        if (removed) return;
        removed = true;
        try {
            modeled.destroy();
        } catch (RuntimeException ignored) {
            // already gone
        }
        dummy.setRemoved(true);
        try {
            ModelEngineAPI.removeModeledEntity(dummy.getUUID());
        } catch (RuntimeException ignored) {
            // not registered
        }
    }
}
