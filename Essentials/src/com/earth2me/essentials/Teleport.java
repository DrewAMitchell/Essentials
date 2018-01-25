package com.earth2me.essentials;

import com.earth2me.essentials.utils.DateUtil;
import com.earth2me.essentials.utils.LocationUtil;
import net.ess3.api.IEssentials;
import net.ess3.api.IUser;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static com.earth2me.essentials.I18n.tl;


public class Teleport implements net.ess3.api.ITeleport {
    private final IUser teleportOwner;
    private final IEssentials ess;
    private TimedTeleport timedTeleport;

    private TeleportType tpType;

    public Teleport(IUser user, IEssentials ess) {
        this.teleportOwner = user;
        this.ess = ess;
        tpType = TeleportType.NORMAL;
    }

    public enum TeleportType {
        TPA,
        BACK,
        NORMAL
    }

    public void cooldown(boolean check) throws Exception {
        final Calendar time = new GregorianCalendar();
        if (teleportOwner.getLastTeleportTimestamp() > 0) {
            // Take the current time, and remove the delay from it.
            final double cooldown = ess.getSettings().getTeleportCooldown();
            final Calendar earliestTime = new GregorianCalendar();
            earliestTime.add(Calendar.SECOND, -(int) cooldown);
            earliestTime.add(Calendar.MILLISECOND, -(int) ((cooldown * 1000.0) % 1000.0));
            // This value contains the most recent time a teleportPlayer could have been used that would allow another use.
            final long earliestLong = earliestTime.getTimeInMillis();

            // When was the last teleportPlayer used?
            final Long lastTime = teleportOwner.getLastTeleportTimestamp();

            if (lastTime > time.getTimeInMillis()) {
                // This is to make sure time didn't get messed up on last teleportPlayer use.
                // If this happens, let's give the user the benifit of the doubt.
                teleportOwner.setLastTeleportTimestamp(time.getTimeInMillis());
                return;
            } else if (lastTime > earliestLong
                    && cooldownApplies()) {
                time.setTimeInMillis(lastTime);
                time.add(Calendar.SECOND, (int) cooldown);
                time.add(Calendar.MILLISECOND, (int) ((cooldown * 1000.0) % 1000.0));
                throw new Exception(tl("timeBeforeTeleport", DateUtil.formatDateDiff(time.getTimeInMillis())));
            }
        }
        // if justCheck is set, don't update lastTeleport; we're just checking
        if (!check) {
            teleportOwner.setLastTeleportTimestamp(time.getTimeInMillis());
        }
    }

    private boolean cooldownApplies() {
        boolean applies = true;
        String globalBypassPerm = "essentials.teleport.cooldown.bypass";
        switch (tpType) {
            case NORMAL:
                applies = !teleportOwner.isAuthorized(globalBypassPerm);
                break;
            case BACK:
                applies = !(teleportOwner.isAuthorized(globalBypassPerm) &&
                        teleportOwner.isAuthorized("essentials.teleport.cooldown.bypass.back"));
                break;
            case TPA:
                applies = !(teleportOwner.isAuthorized(globalBypassPerm) &&
                        teleportOwner.isAuthorized("essentials.teleport.cooldown.bypass.tpa"));
                break;
        }
        return applies;
    }

    private void warnUser(final IUser user, final double delay) {
        Calendar c = new GregorianCalendar();
        c.add(Calendar.SECOND, (int) delay);
        c.add(Calendar.MILLISECOND, (int) ((delay * 1000.0) % 1000.0));
        user.sendMessage(tl("dontMoveMessage", DateUtil.formatDateDiff(c.getTimeInMillis())));
    }

    //The now function is used when you want to skip tp delay when teleporting someone to a location or player.
    @Override
    public void now(Location loc, boolean cooldown, TeleportCause cause) throws Exception {
        if (cooldown) {
            cooldown(false);
        }
        final ITarget target = new LocationTarget(loc);
        now(teleportOwner, target, cause);
    }

    @Override
    public void now(Player targetEntity, boolean cooldown, TeleportCause cause) throws Exception {
        if (cooldown) {
            cooldown(false);
        }
        final ITarget target = new PlayerTarget(targetEntity);
        now(teleportOwner, target, cause);
        teleportOwner.sendMessage(tl("teleporting", target.getLocation().getWorld().getName(), target.getLocation().getBlockX(), target.getLocation().getBlockY(), target.getLocation().getBlockZ()));
    }
    
    protected void now(IUser teleportee, ITarget target, TeleportCause cause) throws Exception {
    	now(teleportee, target, cause, false);
    }

    protected void now(IUser teleportee, ITarget target, TeleportCause cause, boolean overrideCenter) throws Exception {
        cancel(false);
        teleportee.setLastLocation();
        Location loc = target.getLocation();

        if (LocationUtil.isBlockUnsafeForUser(teleportee, loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
            if (ess.getSettings().isTeleportSafetyEnabled()) {
                if (ess.getSettings().isForceDisableTeleportSafety()) {
                    teleportee.getBase().teleport(loc, cause);
                } else {
                    teleportee.getBase().teleport(LocationUtil.getSafeDestination(ess, teleportee, loc, overrideCenter), cause);
                }
            } else {
                throw new Exception(tl("unsafeTeleportDestination", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            }
        } else {
            if (ess.getSettings().isForceDisableTeleportSafety()) {
                teleportee.getBase().teleport(loc, cause);
            } else {
                if (ess.getSettings().isTeleportToCenterLocation() && !overrideCenter) {
                    loc = LocationUtil.getRoundedDestination(loc);
                }
                teleportee.getBase().teleport(loc, cause);
            }
        }
    }

    //The teleportPlayer function is used when you want to normally teleportPlayer someone to a location or player.
    //This method is no longer used internally and will be removed.
    @Deprecated
    @Override
    public void teleport(Location loc, Trade chargeFor) throws Exception {
        teleport(loc, chargeFor, TeleportCause.PLUGIN, false);
    }
    
    public void teleport(Location loc, Trade chargeFor, TeleportCause cause) throws Exception {
        teleport(teleportOwner, new LocationTarget(loc), chargeFor, cause, false);
    }

    @Override
    public void teleport(Location loc, Trade chargeFor, TeleportCause cause, boolean overrideCenter) throws Exception {
        teleport(teleportOwner, new LocationTarget(loc), chargeFor, cause, overrideCenter);
    }

    //This is used when teleporting to a player
    @Override
    public void teleport(Player entity, Trade chargeFor, TeleportCause cause) throws Exception {
        ITarget target = new PlayerTarget(entity);
        teleportOwner.sendMessage(tl("teleportToPlayer", entity.getDisplayName()));
        teleport(teleportOwner, target, chargeFor, cause, false);
    }

    //This is used when teleporting to stored location
    @Override
    public void teleportPlayer(IUser teleportee, Location loc, Trade chargeFor, TeleportCause cause) throws Exception {
        teleport(teleportee, new LocationTarget(loc), chargeFor, cause, false);
    }

    //This is used on /tphere
    @Override
    public void teleportPlayer(IUser teleportee, Player entity, Trade chargeFor, TeleportCause cause) throws Exception {
        ITarget target = new PlayerTarget(entity);
        teleport(teleportee, target, chargeFor, cause, false);
        teleportee.sendMessage(tl("teleporting", target.getLocation().getWorld().getName(), target.getLocation().getBlockX(), target.getLocation().getBlockY(), target.getLocation().getBlockZ()));
        teleportOwner.sendMessage(tl("teleporting", target.getLocation().getWorld().getName(), target.getLocation().getBlockX(), target.getLocation().getBlockY(), target.getLocation().getBlockZ()));
    }

    private void teleport(IUser teleportee, ITarget target, Trade chargeFor, TeleportCause cause, boolean overrideCenter) throws Exception {
        double delay = ess.getSettings().getTeleportDelay();

        Trade cashCharge = chargeFor;

        if (chargeFor != null) {
            chargeFor.isAffordableFor(teleportOwner);

            //This code is to make sure that commandcosts are checked in the initial world, and not in the resulting world.
            if (!chargeFor.getCommandCost(teleportOwner).equals(BigDecimal.ZERO)) {
                //By converting a command cost to a regular cost, the command cost permission isn't checked when executing the charge after teleport.
                cashCharge = new Trade(chargeFor.getCommandCost(teleportOwner), ess);
            }
        }

        cooldown(true);
        if (delay <= 0 || teleportOwner.isAuthorized("essentials.teleport.timer.bypass") || teleportee.isAuthorized("essentials.teleport.timer.bypass")) {
            cooldown(false);
            now(teleportee, target, cause, overrideCenter);
            if (cashCharge != null) {
                cashCharge.charge(teleportOwner);
            }
            return;
        }

        cancel(false);
        warnUser(teleportee, delay);
        initTimer((long) (delay * 1000.0), teleportee, target, cashCharge, cause, false);
    }

    //The respawn function is a wrapper used to handle tp fallback, on /jail and /home
    @Override
    public void respawn(final Trade chargeFor, TeleportCause cause) throws Exception {
        double delay = ess.getSettings().getTeleportDelay();
        if (chargeFor != null) {
            chargeFor.isAffordableFor(teleportOwner);
        }
        cooldown(true);
        if (delay <= 0 || teleportOwner.isAuthorized("essentials.teleport.timer.bypass")) {
            cooldown(false);
            respawnNow(teleportOwner, cause);
            if (chargeFor != null) {
                chargeFor.charge(teleportOwner);
            }
            return;
        }

        cancel(false);
        warnUser(teleportOwner, delay);
        initTimer((long) (delay * 1000.0), teleportOwner, null, chargeFor, cause, true);
    }

    protected void respawnNow(IUser teleportee, TeleportCause cause) throws Exception {
        final Player player = teleportee.getBase();
        Location bed = player.getBedSpawnLocation();
        if (bed != null) {
            now(teleportee, new LocationTarget(bed), cause);
        } else {
            if (ess.getSettings().isDebug()) {
                ess.getLogger().info("Could not find bed spawn, forcing respawn event.");
            }
            final PlayerRespawnEvent pre = new PlayerRespawnEvent(player, player.getWorld().getSpawnLocation(), false);
            ess.getServer().getPluginManager().callEvent(pre);
            now(teleportee, new LocationTarget(pre.getRespawnLocation()), cause);
        }
    }

    //The warp function is a wrapper used to teleportPlayer a player to a /warp
    @Override
    public void warp(IUser teleportee, String warp, Trade chargeFor, TeleportCause cause) throws Exception {
        Location loc = ess.getWarps().getWarp(warp);
        teleportee.sendMessage(tl("warpingTo", warp, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        if (!teleportee.equals(teleportOwner)) {
            teleportOwner.sendMessage(tl("warpingTo", warp, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }
        teleport(teleportee, new LocationTarget(loc), chargeFor, cause, false);
    }

    //The back function is a wrapper used to teleportPlayer a player /back to their previous location.
    @Override
    public void back(Trade chargeFor) throws Exception {
        tpType = TeleportType.BACK;
        final Location loc = teleportOwner.getLastLocation();
        teleportOwner.sendMessage(tl("backUsageMsg", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        teleport(teleportOwner, new LocationTarget(loc), chargeFor, TeleportCause.COMMAND, false);
    }

    //This function is used to throw a user back after a jail sentence
    @Override
    public void back() throws Exception {
        now(teleportOwner, new LocationTarget(teleportOwner.getLastLocation()), TeleportCause.COMMAND);
    }

    public void setTpType(TeleportType tpType) {
        this.tpType = tpType;
    }

    //If we need to cancelTimer a pending teleportPlayer call this method
    private void cancel(boolean notifyUser) {
        if (timedTeleport != null) {
            timedTeleport.cancelTimer(notifyUser);
            timedTeleport = null;
        }
    }

    private void initTimer(long delay, IUser teleportUser, ITarget target, Trade chargeFor, TeleportCause cause, boolean respawn) {
        timedTeleport = new TimedTeleport(teleportOwner, ess, this, delay, teleportUser, target, chargeFor, cause, respawn);
    }
}
