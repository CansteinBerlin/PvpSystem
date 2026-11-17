package me.hasenzahn1.pvp.listeners;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import me.hasenzahn1.pvp.PvpSystem;
import me.hasenzahn1.pvp.commands.PeacefulCommand;
import me.hasenzahn1.pvp.commands.lookup.LookupEntry;
import me.hasenzahn1.pvp.damage.CombatTracker;
import me.hasenzahn1.pvp.database.PlayerDamageEntry;
import me.hasenzahn1.pvp.database.PlayerDeathEntry;
import me.hasenzahn1.pvp.database.PlayerStateEntry;
import me.hasenzahn1.pvp.debug.ObjectPrinter;
import me.hasenzahn1.pvp.utils.EventUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.UUID;

public class DamageListener implements Listener {

    private final CombatTracker combatTracker;

    public static final HashMap<Player, Double> lastDamageForPlayer = new HashMap<>();

    public DamageListener() {
        combatTracker = new CombatTracker();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if(!isPlayer(event.getEntity())) return;

        Player victim = (Player) event.getEntity();
        lastDamageForPlayer.put(victim, event.getFinalDamage());

        Entity attackingEntity = EventUtils.getAttackingEntity(event);
        if(attackingEntity == victim) return; //Ignore Own damage sources
        if(!isPlayer(attackingEntity)) return; // Only PvP should be restricted

        PlayerStateEntry victimEntry = PvpSystem.getInstance().getDatabase().getPlayerStates().get(victim.getUniqueId());
        PlayerStateEntry attackerEntry = PvpSystem.getInstance().getDatabase().getPlayerStates().get(attackingEntity.getUniqueId());
        if(victimEntry.disablePVP || attackerEntry.disablePVP) {
            event.setCancelled(true);
            return;
        }
        PeacefulCommand.PVP_ACTION_TIMESTAMPS.put(attackingEntity.getUniqueId(), System.currentTimeMillis());

        boolean victimPeaceful = victimEntry.state;
        boolean attackerPeaceful = attackerEntry.state;
        if(!victimPeaceful && !attackerPeaceful) return; // Ignore PVP vs PVP

        Player attackingPlayer = ((Player) attackingEntity);
        if(!PvpSystem.getInstance().isMaceEnabledIfPeaceful() && (attackingPlayer.getInventory().getItemInMainHand().getType() == Material.MACE ||
                attackingPlayer.getInventory().getItemInOffHand().getType() == Material.MACE)) {
            event.setCancelled(true);
            return;
        }

        double damage = combatTracker.getMaxDamageToApply(victim, event);
        event.setDamage(damage);
        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
        event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
    }

    @EventHandler
    public void onMaceKnockback(EntityPushedByEntityAttackEvent event){
        if(PvpSystem.getInstance().isMaceEnabledIfPeaceful()) return;
        if(!isPlayer(event.getEntity()) || !isPlayer(event.getPushedBy())) return;
        PlayerStateEntry victimEntry = PvpSystem.getInstance().getDatabase().getPlayerStates().get(event.getEntity().getUniqueId());
        PlayerStateEntry attackerEntry = PvpSystem.getInstance().getDatabase().getPlayerStates().get(event.getPushedBy().getUniqueId());
        if(!victimEntry.state && !attackerEntry.state) return; // Ignore PVP vs PVP

        Player attackingPlayer = ((Player) event.getPushedBy());
        if(attackingPlayer.getInventory().getItemInMainHand().getType() != Material.MACE &&
           attackingPlayer.getInventory().getItemInOffHand().getType() != Material.MACE) return;

        event.setCancelled(true);
    }

    private boolean isPlayer(Entity e) {
        return (e instanceof Player);
    }
}
