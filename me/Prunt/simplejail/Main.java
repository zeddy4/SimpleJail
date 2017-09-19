package me.zeddy;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class Main
  extends JavaPlugin
  implements Listener
{
  public void onEnable()
  {
    saveDefaultConfig();
    
    getServer().getPluginManager().registerEvents(this, this);
    
    getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
    {
      public void run()
      {
        long epoch = System.currentTimeMillis() / 1000L;
        for (Player p : Main.this.getServer().getOnlinePlayers()) {
          if ((Main.this.isJailed(p.getName())) && 
            (epoch > Main.this.getConfig().getLong("players." + p.getName() + ".releasetime"))) {
            Main.this.unjail(p);
          }
        }
      }
    }, 0L, 1200L);
  }
  
  public void onDisable()
  {
    saveConfig();
  }
  
  @EventHandler
  public void OnClickEntity(PlayerInteractEntityEvent e)
  {
    Player p = e.getPlayer();
    ItemStack item = p.getInventory().getItemInMainHand();
    Player rightClicked = (Player)e.getRightClicked();
    if (((item != null) || (item.getType() != Material.AIR)) && 
      (item.getType() == Material.STICK))
    {
      if ((!item.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', getConfig().getString("JailStick.Name")))) || (
        (!(e.getRightClicked() instanceof Player)) || 
        (p.hasPermission(getConfig().getString("JailStick.Permission"))))) {}
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), getConfig().getString("JailStick.Command").replace("%player%", rightClicked.getName()));
      p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("message.Jailed-Player").replace("%player%", rightClicked.getName()).replace("%prefix%", ChatColor.translateAlternateColorCodes('&', getConfig().getString("message.Prefix")))));
      p.playSound(p.getLocation(), Sound.valueOf(getConfig().getString("JailStick.Sound")), 1.0F, 1.0F);
    }
  }
  
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onPlayerJoin(PlayerJoinEvent e)
  {
    Player p = e.getPlayer();
    String pl = p.getName();
    if (isJailed(pl))
    {
      if (jailedOffline(pl))
      {
        toJailAgain(p);
        
        removeJailed(pl);
      }
      if (unjailedOffline(pl))
      {
        unjail(p);
        
        removeUnjailed(pl);
      }
      if (getConfig().getBoolean("options.jail-on-login")) {
        toJailAgain(p);
      }
    }
  }
  
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent e)
  {
    Player p = e.getPlayer();
    String pl = p.getName();
    if (isJailed(pl))
    {
      toJailAgain(p);
      
      p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.Respawn-jailed").replace("%prefix%", ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.Prefix")))));
    }
  }
  
  @EventHandler(priority=EventPriority.HIGHEST)
  public void onCommandPreprocess(PlayerCommandPreprocessEvent e)
  {
    if (e.isCancelled()) {
      return;
    }
    if (isJailed(e.getPlayer().getName()))
    {
      List<String> commands = getConfig().getStringList("commands.filtered-list");
      
      boolean match = false;
      for (String command : commands) {
        if (e.getMessage().startsWith("/" + command))
        {
          match = true;
          break;
        }
      }
      if (getConfig().getString("commands.filter").equalsIgnoreCase("whitelist"))
      {
        if (!match)
        {
          e.getPlayer().sendMessage(getConfig().getString("messages.cant-use-this-command"));
          e.setCancelled(true);
        }
      }
      else if ((getConfig().getString("commads.filter").equalsIgnoreCase("blacklist")) && 
        (match))
      {
        e.getPlayer().sendMessage(getConfig().getString("messages.cant-use-this-command"));
        e.setCancelled(true);
        return;
      }
    }
  }
  
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
  {
    if (cmd.getName().equalsIgnoreCase("jail"))
    {
      if (args.length < 3)
      {
        sender.sendMessage(getConfig().getString("messages.not-enough-arguments"));
        sender.sendMessage(getConfig().getString("messages.correct-usage-jail"));
        return true;
      }
      if (!pointsExist())
      {
        sender.sendMessage(getConfig().getString("messages.jail-unjail-does-not-exist"));
        return true;
      }
      String apl = args[0];
      long time = getUntil(args[1]);
      String until = getTime(time);
      StringBuilder stb = new StringBuilder();
      for (int i = 2; i < args.length; i++) {
        stb.append(args[i]).append(" ");
      }
      String reason = stb.toString();
      if (time == 0L)
      {
        sender.sendMessage(getConfig().getString("messages.wrong-time-format"));
        return true;
      }
      if (isOnline(apl)) {
        jail(getServer().getPlayerExact(apl), time, reason);
      } else {
        jailOffline(apl, time, reason);
      }
      if (isJailed(apl)) {
        sender.sendMessage(getConfig().getString("messages.jail-success")
        
          .replaceAll("%player%", apl).replaceAll("%reason%", reason).replaceAll("%until%", until));
      } else {
        sender.sendMessage(getConfig().getString("messages.jail-fail")
        
          .replaceAll("%player%", apl));
      }
    }
    else if (cmd.getName().equalsIgnoreCase("unjail"))
    {
      if (args.length > 1)
      {
        sender.sendMessage(getConfig().getString("messages.too-many-arguments"));
        return true;
      }
      if (args.length < 1)
      {
        sender.sendMessage(getConfig().getString("messages.not-enough-arguments"));
        return true;
      }
      String apl = args[0];
      if (isJailed(apl))
      {
        if (isOnline(apl)) {
          unjail(getServer().getPlayerExact(apl));
        } else {
          unjailOffline(apl);
        }
      }
      else {
        sender.sendMessage(getConfig().getString("messages.unjail-fail")
        
          .replaceAll("%player%", apl));
      }
    }
    else if (cmd.getName().equalsIgnoreCase("checkjail"))
    {
      if (args.length > 1)
      {
        sender.sendMessage(getConfig().getString("messages.too-many-arguments"));
        return true;
      }
      if (args.length < 1)
      {
        sender.sendMessage(getConfig().getString("messages.not-enough-arguments"));
        return true;
      }
      String apl = args[0];
      if (isJailed(apl))
      {
        String until = getTime(getConfig().getLong("players." + apl + ".releasetime"));
        
        String reason = getConfig().getString("player." + apl + ".reason");
        
        sender.sendMessage(getConfig().getString("messages.checkjail-is-jailed")
        
          .replaceAll("%player%", apl).replaceAll("%until%", until).replaceAll("%reason%", reason));
      }
      else
      {
        sender.sendMessage(getConfig().getString("messages.checkjail-not-jailed")
        
          .replaceAll("%player%", apl));
      }
    }
    else if (cmd.getName().equalsIgnoreCase("setjail"))
    {
      if (!(sender instanceof Player))
      {
        sender.sendMessage(getConfig().getString("messages.console-error"));
        return true;
      }
      if (args.length > 0)
      {
        sender.sendMessage(getConfig().getString("messages.too-many-arguments"));
        return true;
      }
      Player p = (Player)sender;
      
      setJailLoc(p.getLocation());
      
      p.sendMessage(getConfig().getString("messages.setjail-success"));
    }
    else if (cmd.getName().equalsIgnoreCase("setunjail"))
    {
      if (!(sender instanceof Player))
      {
        sender.sendMessage(getConfig().getString("messages.console-error"));
        return true;
      }
      if (args.length > 0)
      {
        sender.sendMessage(getConfig().getString("messages.too-many-arguments"));
        return true;
      }
      Player p = (Player)sender;
      
      setUnjailLoc(p.getLocation());
      
      p.sendMessage(getConfig().getString("messages.setunjail-success"));
    }
    else if (cmd.getName().equalsIgnoreCase("simplejail"))
    {
      reloadConfig();
      
      sender.sendMessage(getConfig().getString("messages.reload"));
    }
    else if (cmd.getName().equalsIgnoreCase("jailstick"))
    {
      Player p = (Player)sender;
      if (p.hasPermission("JailStick.Admin"))
      {
        if (args.length == 0)
        {
          ItemStack jailstick = new ItemStack(Material.STICK, 1);
          ItemMeta jailstickmeta = jailstick.getItemMeta();
          jailstickmeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("JailStick.Name")));
          jailstick.setItemMeta(jailstickmeta);
          p.getInventory().addItem(new ItemStack[] { jailstick });
          p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
          p.updateInventory();
          p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.Obtained-Stick").replace("%prefix%", ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.Prefix")))));
        }
      }
      else {
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.No-Permision").replace("%prefix%", ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.Prefix")))));
      }
    }
    return true;
  }
  
  public void setUnjailLoc(Location loc)
  {
    double x = loc.getX();
    double y = loc.getY();
    double z = loc.getZ();
    float pitch = loc.getPitch();
    float yaw = loc.getYaw();
    String world = loc.getWorld().getName();
    
    getConfig().set("points.unjail.world", world);
    getConfig().set("points.unjail.x", Double.valueOf(x));
    getConfig().set("points.unjail.y", Double.valueOf(y));
    getConfig().set("points.unjail.z", Double.valueOf(z));
    getConfig().set("points.unjail.pitch", Float.valueOf(pitch));
    getConfig().set("points.unjail.yaw", Float.valueOf(yaw));
    
    saveConfig();
  }
  
  public void setJailLoc(Location loc)
  {
    double x = loc.getX();
    double y = loc.getY();
    double z = loc.getZ();
    float pitch = loc.getPitch();
    float yaw = loc.getYaw();
    String world = loc.getWorld().getName();
    
    getConfig().set("points.jail.world", world);
    getConfig().set("points.jail.x", Double.valueOf(x));
    getConfig().set("points.jail.y", Double.valueOf(y));
    getConfig().set("points.jail.z", Double.valueOf(z));
    getConfig().set("points.jail.pitch", Float.valueOf(pitch));
    getConfig().set("points.jail.yaw", Float.valueOf(yaw));
    
    saveConfig();
  }
  
  public boolean isJailed(String pl)
  {
    return getConfig().isSet("players." + pl);
  }
  
  private String getTime(long time)
  {
    Date date = new Date(time * 1000L);
    
    DateFormat format = new SimpleDateFormat(getConfig().getString("options.timeformat"));
    
    format.setTimeZone(Calendar.getInstance().getTimeZone());
    
    return format.format(date);
  }
  
  public void tpToJail(Player p)
  {
    double x = getConfig().getDouble("points.jail.x");
    double y = getConfig().getDouble("points.jail.y");
    double z = getConfig().getDouble("points.jail.z");
    float pitch = (float)getConfig().getDouble("points.jail.pitch");
    float yaw = (float)getConfig().getDouble("points.jail.yaw");
    World world = getServer().getWorld(getConfig().getString("points.jail.world"));
    
    Location loc = new Location(world, x, y, z, yaw, pitch);
    
    p.teleport(loc);
  }
  
  public void unjail(Player p)
  {
    getConfig().set("players." + p.getName(), null);
    
    saveConfig();
    
    double x = getConfig().getDouble("points.unjail.x");
    double y = getConfig().getDouble("points.unjail.y");
    double z = getConfig().getDouble("points.unjail.z");
    float pitch = (float)getConfig().getDouble("points.unjail.pitch");
    float yaw = (float)getConfig().getDouble("points.unjail.yaw");
    World world = getServer().getWorld(getConfig().getString("points.unjail.world"));
    
    Location loc = new Location(world, x, y, z, yaw, pitch);
    
    p.teleport(loc);
    
    p.sendMessage(getConfig().getString("messages.you-have-been-unjailed"));
  }
  
  public void unjailOffline(String pl)
  {
    List<String> list = new ArrayList();
    if (getConfig().isSet("unjailed")) {
      list = getConfig().getStringList("unjailed");
    }
    list.add(pl);
    
    getConfig().set("unjailed", list);
    
    saveConfig();
  }
  
  public boolean jailedOffline(String pl)
  {
    return getConfig().isSet("jailed." + pl);
  }
  
  public boolean unjailedOffline(String pl)
  {
    return getConfig().isSet("unjailed." + pl);
  }
  
  private void toJailAgain(Player p)
  {
    String pl = p.getName();
    
    tpToJail(p);
    
    String until = getTime(getConfig().getLong("players." + pl + ".releasetime"));
    
    String reason = getConfig().getString("player." + pl + ".reason");
    
    p.sendMessage(getConfig().getString("messages.still-jailed")
    
      .replaceAll("%reason%", reason).replaceAll("%until%", until));
  }
  
  public boolean pointsExist()
  {
    return (getConfig().isSet("points.jail")) && (getConfig().isSet("points.unjail"));
  }
  
  private long getUntil(String time)
  {
    if (!time.matches("(.*)(m|w|k|n|d|p|h|t)(.*)")) {
      return 0L;
    }
    String[] list = time.split("/(,?\\s+)|((?<=[a-z])(?=\\d))|((?<=\\d)(?=[a-z]))/i");
    
    long until = System.currentTimeMillis() / 1000L;
    String[] arrayOfString1;
    int j = (arrayOfString1 = list).length;
    for (int i = 0; i < j; i++)
    {
      String str = arrayOfString1[i];
      
      String name = str.replaceAll("\\d", "");
      int nr = Integer.parseInt(str.replaceAll("\\D", ""));
      if ((name.equalsIgnoreCase("months")) || (name.equalsIgnoreCase("month")) || (name.equalsIgnoreCase("mon"))) {
        until += nr * 60 * 60 * 24 * 7 * 4;
      } else if ((name.equalsIgnoreCase("weeks")) || (name.equalsIgnoreCase("week")) || (name.equalsIgnoreCase("w"))) {
        until += nr * 60 * 60 * 24 * 7;
      } else if ((name.equalsIgnoreCase("days")) || (name.equalsIgnoreCase("day")) || (name.equalsIgnoreCase("d"))) {
        until += nr * 60 * 60 * 24;
      } else if ((name.equalsIgnoreCase("hours")) || (name.equalsIgnoreCase("hour")) || (name.equalsIgnoreCase("h"))) {
        until += nr * 60 * 60;
      } else if ((name.equalsIgnoreCase("minutes")) || (name.equalsIgnoreCase("minute")) || 
        (name.equalsIgnoreCase("min")) || (name.equalsIgnoreCase("m"))) {
        until += nr * 60;
      }
    }
    return until;
  }
  
  private boolean isOnline(String pl)
  {
    return getServer().getPlayerExact(pl) != null;
  }
  
  public void jail(Player p, long time, String reason)
  {
    addToConfig(p.getName(), time, reason);
    
    tpToJail(p);
    
    String until = getTime(time);
    
    p.sendMessage(getConfig().getString("messages.you-have-been-jailed")
    
      .replaceAll("%until%", until).replaceAll("%reason%", reason));
  }
  
  public void jailOffline(String pl, long time, String reason)
  {
    addToConfig(pl, time, reason);
    
    List<String> list = new ArrayList();
    if (getConfig().isSet("jailed")) {
      list = getConfig().getStringList("jailed");
    }
    list.add(pl);
    
    getConfig().set("jailed", list);
    
    saveConfig();
  }
  
  private void addToConfig(String pl, long time, String reason)
  {
    getConfig().set("players." + pl + ".releasetime", Long.valueOf(time));
    getConfig().set("players." + pl + ".reason", reason);
    
    saveConfig();
  }
  
  private void removeJailed(String pl)
  {
    List<String> list = new ArrayList();
    if (getConfig().isSet("jailed")) {
      list = getConfig().getStringList("jailed");
    }
    list.remove(pl);
    
    getConfig().set("jailed", list);
    
    saveConfig();
  }
  
  private void removeUnjailed(String pl)
  {
    List<String> list = new ArrayList();
    if (getConfig().isSet("unjailed")) {
      list = getConfig().getStringList("unjailed");
    }
    list.remove(pl);
    
    getConfig().set("unjailed", list);
    
    saveConfig();
  }
}
