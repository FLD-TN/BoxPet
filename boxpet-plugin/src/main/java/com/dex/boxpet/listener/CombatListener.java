package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.ability.AbilityService;
import com.dex.boxpet.gui.Menu;
import com.dex.boxpet.pet.ActivePet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

/** Combat tags, the mark bonus and the shield (spec 6, 11.1). */
public final class CombatListener implements Listener {

    private final BoxPetPlugin plugin;

    public CombatListener(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    private static @Nullable LivingEntity attacker(Entity damager) {
        if (damager instanceof LivingEntity le) return le;
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof LivingEntity le) return le;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof LivingEntity le) return le;
        return null;
    }

    /** Mark: the owner's own hits on a marked target deal more. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarkedHit(EntityDamageByEntityEvent e) {
        if (AbilityService.dealing()) return;
        LivingEntity a = attacker(e.getDamager());
        if (!(a instanceof Player p)) return;
        double mult = plugin.abilityService().markMultiplier(p.getUniqueId(), e.getEntity().getUniqueId());
        if (mult > 1) e.setDamage(e.getDamage() * mult);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        LivingEntity a = attacker(e.getDamager());
        if (a == null || a == victim) return;
        Player attackerPlayer = a instanceof Player p ? p : null;
        if (AbilityService.dealing()) {
            // 0.13.3: a pet skill hits as its owner; it must not count as the owner (or the victim) choosing a fight,
            // else every pet hit renews the owner's target + tag and the victim's pet answers: pets fought forever.
            // The victim is still kept in combat (no menus while a pet hits them).
            if (victim instanceof Player vp) plugin.pets().combat().tagOnly(vp);
            return;
        }
        plugin.pets().combat().hit(attackerPlayer, victim, a);
        if (plugin.settings().closeMenuOnCombat) {
            if (attackerPlayer != null) closeMenu(attackerPlayer);
            if (victim instanceof Player vp) closeMenu(vp);
        }
    }

    private void closeMenu(Player p) {
        if (com.dex.boxpet.listener.InteractListener.bypass(p)) return;
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
            p.closeInventory();
            plugin.messages().send(p, "combat-locked", "seconds", String.valueOf(plugin.interact().combatWait(p)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ActivePet pet = plugin.pets().active(p.getUniqueId());
        if (pet == null) return;
        double after = p.getHealth() - e.getFinalDamage();
        LivingEntity by = e instanceof EntityDamageByEntityEvent ed ? attacker(ed.getDamager()) : null;
        plugin.abilityService().onOwnerDamaged(pet, after, by == p ? null : by);
    }
}
