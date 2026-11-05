package de.unstable.survival;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class UnstableSurvival extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey itemKey;
    private File economyFile, deadFile, ahFile;
    private YamlConfiguration economy, dead, auction;
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingRtp = new HashMap<>();
    private final Map<String, ItemSpec> specs = new LinkedHashMap<>();
    private final Map<UUID, String> activeOrder = new HashMap<>();
    private final Map<UUID, Long> abilityCooldown = new HashMap<>();
    private final Random random = new Random();

    private record ItemSpec(String id, String title, Material icon, String rarity, String ability, String[] shape, Map<Character, Material> ingredients) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String f : List.of("shop.yml","orders.yml","auction-house.yml","economy.yml","rtp.yml","custom-items.yml","recipes.yml","messages.yml")) saveIfMissing(f);
        itemKey = new NamespacedKey(this, "custom_item");
        economyFile = new File(getDataFolder(), "economy.yml"); deadFile = new File(getDataFolder(), "dead.yml"); ahFile = new File(getDataFolder(), "auction-house.yml");
        economy = YamlConfiguration.loadConfiguration(economyFile); dead = YamlConfiguration.loadConfiguration(deadFile); auction = YamlConfiguration.loadConfiguration(ahFile);
        registerItemsAndRecipes();
        World survival = Bukkit.getWorld(getConfig().getString("survival.world", "hardcore"));
        if (survival == null) survival = Bukkit.createWorld(new WorldCreator(getConfig().getString("survival.world", "hardcore")));
        if (survival != null) { survival.setDifficulty(Difficulty.HARD); survival.setGameRule(GameRule.NATURAL_REGENERATION, true); }
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String cmd : List.of("survival","rtp","shop","orders","ah","money","pay","baltop","customitems","us","usadmin")) {
            PluginCommand c = getCommand(cmd); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) enforceHardcore(p);
        getLogger().info("UnstableSurvival wurde aktiviert.");
    }
    @Override public void onDisable() { saveAll(); for (BukkitTask t : pendingRtp.values()) t.cancel(); }
    private void saveIfMissing(String name) { File f = new File(getDataFolder(), name); if (!f.exists()) saveResource(name, false); }
    private void saveAll() { save(economy,economyFile); save(dead,deadFile); save(auction,ahFile); }
    private void save(YamlConfiguration y, File f) { try { y.save(f); } catch (IOException e) { getLogger().severe("Speichern fehlgeschlagen: " + f.getName() + " - " + e.getMessage()); } }

    private void addSpec(String id,String title,Material icon,String rarity,String ability,String[] shape,Map<Character,Material> ing) {
        specs.put(id,new ItemSpec(id,title,icon,rarity,ability,shape,ing));
    }
    private Map<Character,Material> ingredients(Object... pairs) { Map<Character,Material> m=new HashMap<>(); for(int i=0;i<pairs.length;i+=2)m.put((Character)pairs[i],(Material)pairs[i+1]); return m; }
    private void registerItemsAndRecipes() {
        addSpec("orbital_wither_skull","Orbital Wither Skull",Material.WITHER_SKELETON_SKULL,"Legendary","wither",new String[]{"ABA","CDC","AEA"},ingredients('A',Material.NETHERITE_SCRAP,'B',Material.NETHERITE_SCRAP,'C',Material.ECHO_SHARD,'D',Material.NETHER_STAR,'E',Material.WITHER_SKELETON_SKULL));
        addSpec("dragon_orbital","Dragon Orbital",Material.DRAGON_HEAD,"Mythic","dragon",new String[]{"ABA","CEC","DBD"},ingredients('A',Material.ECHO_SHARD,'B',Material.DRAGON_BREATH,'C',Material.END_CRYSTAL,'E',Material.ELYTRA,'D',Material.NETHER_STAR));
        addSpec("crystal_orbital","Crystal Orbital",Material.AMETHYST_CLUSTER,"Epic","crystal",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.AMETHYST_BLOCK,'B',Material.END_CRYSTAL,'C',Material.NETHER_STAR));
        addSpec("wind_orbital","Wind Orbital",Material.BREEZE_ROD,"Epic","wind",new String[]{"ABA","CDC","AEA"},ingredients('A',Material.BREEZE_ROD,'B',Material.WIND_CHARGE,'C',Material.PHANTOM_MEMBRANE,'D',Material.ECHO_SHARD,'E',Material.WIND_CHARGE));
        addSpec("shadow_spear","Shadow Spear",Material.TRIDENT,"Epic","shadow_spear",new String[]{"ABA","BCB"," A "},ingredients('A',Material.NETHERITE_INGOT,'B',Material.ECHO_SHARD,'C',Material.TRIDENT));
        addSpec("titan_mace","Titan Mace",Material.MACE,"Mythic","titan",new String[]{"ABA","BCB"," A "},ingredients('A',Material.NETHERITE_INGOT,'B',Material.HEAVY_CORE,'C',Material.MACE));
        addSpec("storm_trident","Storm Trident",Material.TRIDENT,"Legendary","storm",new String[]{"ABA","BCB"," A "},ingredients('A',Material.BREEZE_ROD,'B',Material.LIGHTNING_ROD,'C',Material.TRIDENT));
        addSpec("void_sword","Void Sword",Material.NETHERITE_SWORD,"Epic","void",new String[]{" A ","ABA"," C "},ingredients('A',Material.ENDER_EYE,'B',Material.NETHERITE_SWORD,'C',Material.ECHO_SHARD));
        addSpec("chaos_axe","Chaos Axe",Material.NETHERITE_AXE,"Legendary","chaos",new String[]{"ABA","BCB"," C "},ingredients('A',Material.AMETHYST_BLOCK,'B',Material.NETHER_STAR,'C',Material.NETHERITE_AXE));
        addSpec("dragon_blade","Dragon Blade",Material.NETHERITE_SWORD,"Mythic","dragon_blade",new String[]{" A ","ABA"," C "},ingredients('A',Material.DRAGON_BREATH,'B',Material.DRAGON_HEAD,'C',Material.NETHERITE_SWORD));
        addSpec("phantom_bow","Phantom Bow",Material.BOW,"Epic","phantom",new String[]{"ABA","C C","ABA"},ingredients('A',Material.PHANTOM_MEMBRANE,'B',Material.ECHO_SHARD,'C',Material.BOW));
        addSpec("inferno_crossbow","Inferno Crossbow",Material.CROSSBOW,"Epic","inferno",new String[]{"ABA","CDC"," A "},ingredients('A',Material.BLAZE_ROD,'B',Material.FIRE_CHARGE,'C',Material.CROSSBOW,'D',Material.AIR));
        addSpec("chaos_core","Chaos Core",Material.NETHER_STAR,"Rare","material",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.ECHO_SHARD,'B',Material.AMETHYST_SHARD,'C',Material.NETHER_STAR));
        addSpec("shadow_crystal","Shadow Crystal",Material.AMETHYST_SHARD,"Rare","material",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.OBSIDIAN,'B',Material.ECHO_SHARD,'C',Material.ENDER_EYE));
        addSpec("nether_core","Nether Core",Material.BLAZE_ROD,"Rare","material",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.NETHERITE_SCRAP,'B',Material.BLAZE_ROD,'C',Material.NETHER_STAR));
        addSpec("dragon_heart","Dragon Heart",Material.DRAGON_BREATH,"Legendary","material",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.DRAGON_BREATH,'B',Material.GOLD_BLOCK,'C',Material.NETHER_STAR));
        addSpec("orbital_fragment","Orbital Fragment",Material.PRISMARINE_CRYSTALS,"Uncommon","material",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.AMETHYST_SHARD,'B',Material.PRISMARINE_CRYSTALS,'C',Material.ECHO_SHARD));
        addSpec("orbital_totem","Orbital Totem",Material.TOTEM_OF_UNDYING,"Legendary","totem",new String[]{"ABA","BCB"," A "},ingredients('A',Material.GOLD_BLOCK,'B',Material.ECHO_SHARD,'C',Material.TOTEM_OF_UNDYING));
        addSpec("orbital_heart","Orbital Heart",Material.HEART_OF_THE_SEA,"Epic","heart",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.GOLD_BLOCK,'B',Material.AMETHYST_BLOCK,'C',Material.NETHER_STAR));
        addSpec("orbital_eye","Orbital Eye",Material.ENDER_EYE,"Rare","eye",new String[]{"ABA","BCB","ABA"},ingredients('A',Material.ECHO_SHARD,'B',Material.AMETHYST_SHARD,'C',Material.ENDER_EYE));
        addSpec("ancient_key","Ancient Key",Material.TRIPWIRE_HOOK,"Rare","key",new String[]{" A ","BCB"," A "},ingredients('A',Material.GOLD_INGOT,'B',Material.ECHO_SHARD,'C',Material.HEAVY_CORE));
        addSpec("explosive_arrow","Explosive Arrow",Material.TIPPED_ARROW,"Uncommon","explosive_arrow",new String[]{"ABA","CDC","ABA"},ingredients('A',Material.ARROW,'B',Material.FIRE_CHARGE,'C',Material.GUNPOWDER,'D',Material.AIR));
        addSpec("custom_tnt","Custom TNT",Material.TNT,"Rare","tnt",new String[]{"ABA","CDC","ABA"},ingredients('A',Material.TNT,'B',Material.NETHERITE_SCRAP,'C',Material.BLAZE_POWDER,'D',Material.ECHO_SHARD));
        addSpec("special_fireball","Special Fireball",Material.FIRE_CHARGE,"Rare","fireball",new String[]{"ABA","CDC"," A "},ingredients('A',Material.FIRE_CHARGE,'B',Material.BLAZE_ROD,'C',Material.GHAST_TEAR,'D',Material.MAGMA_CREAM));
        addSpec("orbital_ender_pearl","Orbital Ender Pearl",Material.ENDER_PEARL,"Epic","pearl",new String[]{"ABA","CDC","ABA"},ingredients('A',Material.ENDER_PEARL,'B',Material.ECHO_SHARD,'C',Material.CHORUS_FRUIT,'D',Material.ENDER_EYE));
        for (ItemSpec s : specs.values()) registerRecipe(s);
    }
    private void registerRecipe(ItemSpec s) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this,s.id), customItem(s.id,1)); r.shape(s.shape);
        for (var e:s.ingredients.entrySet()) r.setIngredient(e.getKey(),e.getValue());
        try { Bukkit.addRecipe(r); } catch (IllegalArgumentException ignored) { getLogger().warning("Rezept konnte nicht registriert werden: "+s.id); }
    }
    private ItemStack customItem(String id,int amount) {
        ItemSpec s=specs.get(id); if(s==null)return new ItemStack(Material.BARRIER);
        ItemStack it=new ItemStack(s.icon,Math.max(1,Math.min(amount,s.icon.getMaxStackSize()))); ItemMeta meta=it.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.BOLD+s.title);
        meta.setLore(List.of(ChatColor.GRAY+"Seltenheit: "+rarityColor(s.rarity)+s.rarity,ChatColor.DARK_GRAY+"UnstableSurvival",ChatColor.YELLOW+"Fähigkeit: "+ChatColor.WHITE+abilityName(s.ability),ChatColor.GRAY+"Rechtsklick zum Aktivieren",ChatColor.DARK_GRAY+"ID: "+s.id));
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,id); it.setItemMeta(meta); return it;
    }
    private String rarityColor(String r){return switch(r){case "Mythic"->ChatColor.DARK_PURPLE.toString();case "Legendary"->ChatColor.GOLD.toString();case "Epic"->ChatColor.LIGHT_PURPLE.toString();case "Rare"->ChatColor.BLUE.toString();default->ChatColor.GREEN.toString();};}
    private String abilityName(String a){return switch(a){case "material"->"Herstellungsmaterial";case "dragon"->"Drachenstoß";case "wither"->"Wither-Projektil";case "wind"->"Windstoß";case "crystal"->"Kristall-Schutz";case "storm"->"Blitzruf";case "tnt"->"Kontrollierte Detonation";case "pearl"->"Orbital-Teleport";default->a.replace('_',' ');};}
    private String customId(ItemStack item){if(item==null||!item.hasItemMeta())return null;return item.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);}

    @EventHandler public void onJoin(PlayerJoinEvent e){enforceHardcore(e.getPlayer());}
    private boolean inSurvival(Player p){return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("survival.world","hardcore"));}
    private boolean isDead(UUID id){return dead.getBoolean("dead."+id,false);}
    private void enforceHardcore(Player p){if(isDead(p.getUniqueId())){p.setGameMode(GameMode.SPECTATOR);p.sendMessage(ChatColor.RED+"Du bist im Hardcore-Survival ausgeschieden.");}}
    @EventHandler public void onDeath(PlayerDeathEvent e){Player p=e.getEntity();if(!inSurvival(p)||!getConfig().getBoolean("survival.hardcore",true))return;dead.set("dead."+p.getUniqueId(),true);save(dead,deadFile);Bukkit.getScheduler().runTask(this,()->{p.setGameMode(GameMode.SPECTATOR);p.sendMessage(ChatColor.DARK_RED+"☠ Hardcore: Dein Survival-Lauf ist beendet.");});}
    @EventHandler public void onRespawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(isDead(p.getUniqueId())&&inSurvival(p)){Bukkit.getScheduler().runTask(this,()->p.setGameMode(GameMode.SPECTATOR));}}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] a){String c=command.getName().toLowerCase(Locale.ROOT);
        if(c.equals("survival")){if(!(sender instanceof Player p))return true;if(isDead(p.getUniqueId())){p.sendMessage(ChatColor.RED+"Du bist ausgeschieden und kannst nicht erneut beitreten.");return true;}World w=Bukkit.getWorld(getConfig().getString("survival.world","hardcore"));if(w==null){p.sendMessage(ChatColor.RED+"Survival-Welt nicht gefunden.");return true;}w.setDifficulty(Difficulty.HARD);Location spawn=new Location(w,getConfig().getDouble("survival.spawn.x",.5),getConfig().getDouble("survival.spawn.y",100),getConfig().getDouble("survival.spawn.z",.5));p.teleport(spawn);p.setGameMode(GameMode.SURVIVAL);p.sendMessage(ChatColor.GREEN+"Willkommen im Hardcore-Survival. Du hast nur ein Leben!");return true;}
        if(c.equals("rtp")){if(!(sender instanceof Player p))return true;if(a.length>0&&a[0].equalsIgnoreCase("world")){p.sendMessage(ChatColor.YELLOW+"RTP-Welten: "+String.join(", ",getConfig().getStringList("rtp.worlds")));return true;}if(a.length>0&&a[0].equalsIgnoreCase("cooldown")){long left=Math.max(0,(rtpCooldown.getOrDefault(p.getUniqueId(),0L)-System.currentTimeMillis())/1000);p.sendMessage(ChatColor.YELLOW+"RTP-Cooldown: "+left+" Sekunden");return true;}startRtp(p);return true;}
        if(c.equals("shop")){if(sender instanceof Player p)openShop(p);return true;}
        if(c.equals("orders")){if(sender instanceof Player p)openOrders(p);return true;}
        if(c.equals("customitems")){if(sender instanceof Player p)openCustomItems(p);return true;}
        if(c.equals("money")){if(sender instanceof Player p)p.sendMessage(ChatColor.GOLD+"Guthaben: "+format(balance(p.getUniqueId())));return true;}
        if(c.equals("pay")){if(!(sender instanceof Player p)||a.length!=2)return false;Player target=Bukkit.getPlayerExact(a[0]);double amount=parseAmount(a[1]);if(target==null||target.equals(p)||amount<=0||!Double.isFinite(amount)){p.sendMessage(ChatColor.RED+"Verwendung: /pay <Spieler> <Betrag>");return true;}if(!withdraw(p.getUniqueId(),amount)){p.sendMessage(ChatColor.RED+"Nicht genug Geld.");return true;}deposit(target.getUniqueId(),amount);p.sendMessage(ChatColor.GREEN+"Überwiesen: "+format(amount));target.sendMessage(ChatColor.GREEN+"Du hast "+format(amount)+" von "+p.getName()+" erhalten.");return true;}
       if(c.equals("baltop")){List<String> lines=new ArrayList<>();ConfigurationSection sec=economy.getConfigurationSection("balances");if(sec!=null)for(String id:sec.getKeys(false))lines.add(id+";"+sec.getDouble(id));lines.sort((x,y)->Double.compare(Double.parseDouble(y.split(";")[1]),Double.parseDouble(x.split(";")[1])));sender.sendMessage(ChatColor.GOLD+"--- Reichstenliste ---");for(int i=0;i<Math.min(10,lines.size());i++){String[] z=lines.get(i).split(";");OfflinePlayer op=Bukkit.getOfflinePlayer(UUID.fromString(z[0]));sender.sendMessage(ChatColor.YELLOW.toString()+(i+1)+". "+op.getName()+" - "+format(Double.parseDouble(z[1])));}return true;}
        if(c.equals("ah")){if(!(sender instanceof Player p))return true;if(a.length>0&&a[0].equalsIgnoreCase("sell")){if(a.length<2)return false;double price=parseAmount(a[1]);if(price<=0||!Double.isFinite(price)){p.sendMessage(ChatColor.RED+"Ungültiger Preis.");return true;}sellAuction(p,price);return true;}openAh(p);return true;}
        if(c.equals("us")||c.equals("usadmin")){if(!sender.hasPermission("unstablesurvival.admin")){sender.sendMessage(ChatColor.RED+"Keine Berechtigung.");return true;}if(a.length==0||a[0].equalsIgnoreCase("reload")){reloadConfig();sender.sendMessage(ChatColor.GREEN+"Config neu geladen. Für Rezeptänderungen Server neu starten.");return true;}if(a[0].equalsIgnoreCase("money")&&a.length==4&&a[1].equalsIgnoreCase("give")){Player t=Bukkit.getPlayerExact(a[2]);double v=parseAmount(a[3]);if(t!=null&&v>0&&Double.isFinite(v)){deposit(t.getUniqueId(),v);sender.sendMessage("Geld hinzugefügt.");}return true;}if(a[0].equalsIgnoreCase("item")&&a.length==4&&a[1].equalsIgnoreCase("give")){Player t=Bukkit.getPlayerExact(a[2]);ItemStack it=customItem(a[3].toLowerCase(Locale.ROOT),1);if(t!=null&&!it.getType().isAir()){t.getInventory().addItem(it);sender.sendMessage("Item gegeben.");}return true;}if(a[0].equalsIgnoreCase("ah")&&a.length>1&&a[1].equalsIgnoreCase("clear")){auction.set("listings",null);save(auction,ahFile);sender.sendMessage("Auktionen gelöscht.");return true;}if(a[0].equalsIgnoreCase("rtp")&&a.length>1&&a[1].equalsIgnoreCase("set")&&sender instanceof Player p){getConfig().set("rtp.center.world",p.getWorld().getName());getConfig().set("rtp.center.x",p.getLocation().getX());getConfig().set("rtp.center.z",p.getLocation().getZ());saveConfig();sender.sendMessage("RTP-Zentrum gesetzt.");return true;}sender.sendMessage("/us reload | /us money give <spieler> <betrag> | /us item give <spieler> <id> | /us ah clear | /us rtp set");return true;}
        return false;
    }
    private double parseAmount(String s){try{return Double.parseDouble(s);}catch(Exception e){return -1;}}
    private String format(double v){return String.format(Locale.US,"%.2f%s",v,getConfig().getString("economy.currency","$"));}
    private double balance(UUID id){return economy.getDouble("balances."+id,getConfig().getDouble("economy.starting-balance",100));}
    private void deposit(UUID id,double amount){if(amount>0&&Double.isFinite(amount)){economy.set("balances."+id,balance(id)+amount);save(economy,economyFile);}}
    private boolean withdraw(UUID id,double amount){if(amount<=0||!Double.isFinite(amount)||balance(id)+1e-8<amount)return false;economy.set("balances."+id,balance(id)-amount);save(economy,economyFile);return true;}

    private void startRtp(Player p){long now=System.currentTimeMillis();long end=rtpCooldown.getOrDefault(p.getUniqueId(),0L);if(!p.hasPermission("unstablesurvival.bypass.rtp")&&end>now){p.sendMessage(ChatColor.RED+"RTP ist noch "+((end-now+999)/1000)+"s gesperrt.");return;}if(pendingRtp.containsKey(p.getUniqueId())){p.sendMessage(ChatColor.RED+"Du hast bereits einen RTP-Countdown.");return;}Location start=p.getLocation().clone();int seconds=getConfig().getInt("rtp.countdown-seconds",5);p.sendMessage(ChatColor.AQUA+"RTP in "+seconds+" Sekunden. Bewege dich nicht!");final int[] tick={seconds};BukkitTask task=Bukkit.getScheduler().runTaskTimer(this,()->{if(!p.isOnline()||p.getLocation().distanceSquared(start)>0.25){BukkitTask t=pendingRtp.remove(p.getUniqueId());if(t!=null)t.cancel();if(p.isOnline())p.sendMessage(ChatColor.RED+"RTP abgebrochen.");return;}if(tick[0]--<=0){BukkitTask t=pendingRtp.remove(p.getUniqueId());if(t!=null)t.cancel();Location dest=findSafeRtp(p.getWorld());if(dest==null){p.sendMessage(ChatColor.RED+"Kein sicherer Ort gefunden.");return;}p.teleport(dest);if(!p.hasPermission("unstablesurvival.bypass.rtp"))rtpCooldown.put(p.getUniqueId(),System.currentTimeMillis()+getConfig().getLong("rtp.cooldown-seconds",600)*1000);p.sendMessage(ChatColor.GREEN+"Du wurdest sicher teleportiert.");}},20L,20L);pendingRtp.put(p.getUniqueId(),task);}
    private Location findSafeRtp(World w){double cx=getConfig().getDouble("rtp.center.x",w.getSpawnLocation().getX()),cz=getConfig().getDouble("rtp.center.z",w.getSpawnLocation().getZ());int min=getConfig().getInt("rtp.min-distance",500),max=getConfig().getInt("rtp.max-distance",5000),margin=getConfig().getInt("rtp.border-margin",32);WorldBorder b=w.getWorldBorder();for(int i=0;i<getConfig().getInt("rtp.attempts",40);i++){double angle=random.nextDouble()*Math.PI*2,dist=min+random.nextDouble()*Math.max(1,max-min);int x=(int)Math.floor(cx+Math.cos(angle)*dist),z=(int)Math.floor(cz+Math.sin(angle)*dist);Location center=b.getCenter();double half=b.getSize()/2-margin;if(Math.abs(x-center.getX())>half||Math.abs(z-center.getZ())>half)continue;int y=w.getHighestBlockYAt(x,z);Block ground=w.getBlockAt(x,y,z),feet=w.getBlockAt(x,y+1,z),head=w.getBlockAt(x,y+2,z);if(!ground.getType().isSolid()||!feet.isPassable()||!head.isPassable())continue;if(bad(ground.getType())||bad(feet.getType())||bad(head.getType()))continue;if(!getConfig().getBoolean("rtp.allow-underground",false)&&y<w.getSeaLevel()-5)continue;return new Location(w,x+.5,y+1,z+.5);}return null;}
    private boolean bad(Material m){return m==Material.LAVA||m==Material.WATER||m==Material.FIRE||m==Material.SOUL_FIRE||m==Material.CACTUS||m==Material.MAGMA_BLOCK||m==Material.POWDER_SNOW||m==Material.CAMPFIRE||m==Material.SOUL_CAMPFIRE;}

    private Inventory menu(String title,int size){Inventory inv=Bukkit.createInventory(null,size,ChatColor.DARK_AQUA+title);ItemStack filler=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);ItemMeta fm=filler.getItemMeta();fm.setDisplayName(" ");filler.setItemMeta(fm);for(int i=0;i<size;i++)inv.setItem(i,filler);return inv;}
    private ItemStack button(Material m,String name,String... lore){ItemStack it=new ItemStack(m);ItemMeta im=it.getItemMeta();im.setDisplayName(ChatColor.AQUA+name);im.setLore(Arrays.asList(lore));it.setItemMeta(im);return it;}
    private void openShop(Player p){Inventory inv=menu("Shop - Kaufen",54);YamlConfiguration shop=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"shop.yml"));ConfigurationSection s=shop.getConfigurationSection("items");if(s!=null){int slot=0;for(String key:s.getKeys(false)){if(slot>=45)break;Material m=Material.matchMaterial(key);if(m==null)continue;double buy=s.getDouble(key+".buy"),sell=s.getDouble(key+".sell");inv.setItem(slot++,button(m,key,"Kaufen: "+format(buy),"Verkaufen: "+format(sell),"Linksklick: kaufen 1x","Rechtsklick: verkaufen 1x"));}}p.openInventory(inv);}
    private void openOrders(Player p){Inventory inv=menu("Aufträge",27);YamlConfiguration y=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"orders.yml"));ConfigurationSection s=y.getConfigurationSection("orders");if(s!=null){int slot=10;for(String id:s.getKeys(false)){Material m=Material.matchMaterial(s.getString(id+".material","STONE"));inv.setItem(slot++,button(m==null?Material.STONE:m,s.getString(id+".title",id),"Benötigt: "+s.getInt(id+".amount"),"Belohnung: "+format(s.getDouble(id+".reward")),"Klicke zum Annehmen"));if(slot==17)break;}}p.openInventory(inv);}
    private void openCustomItems(Player p){Inventory inv=menu("Custom Items",54);int slot=0;for(ItemSpec s:specs.values()){if(slot>=45)break;inv.setItem(slot++,customItem(s.id,1));}p.openInventory(inv);}
    private void openAh(Player p){Inventory inv=menu("Auktionshaus",54);ConfigurationSection sec=auction.getConfigurationSection("listings");int slot=0;if(sec!=null)for(String id:sec.getKeys(false)){if(slot>=45)break;long expires=auction.getLong("listings."+id+".expires");if(expires<System.currentTimeMillis())continue;ItemStack item=auction.getItemStack("listings."+id+".item");if(item==null)continue;ItemStack display=item.clone();ItemMeta meta=display.getItemMeta();List<String> lore=meta.hasLore()?meta.getLore():new ArrayList<>();lore.add(ChatColor.GOLD+"Preis: "+format(auction.getDouble("listings."+id+".price")));lore.add(ChatColor.GRAY+"Verkäufer: "+auction.getString("listings."+id+".sellerName"));lore.add(ChatColor.DARK_GRAY+"Listing-ID: "+id);meta.setLore(lore);display.setItemMeta(meta);inv.setItem(slot++,display);}p.openInventory(inv);}
    private void sellAuction(Player p,double price){ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType().isAir()){p.sendMessage(ChatColor.RED+"Halte das Verkaufsitem in der Hand.");return;}int count=0;ConfigurationSection sec=auction.getConfigurationSection("listings");if(sec!=null)for(String id:sec.getKeys(false))if(auction.getString("listings."+id+".seller").equals(p.getUniqueId().toString()))count++;if(count>=getConfig().getInt("auction.max-listings-per-player",5)){p.sendMessage(ChatColor.RED+"Du hast zu viele aktive Angebote.");return;}String id=UUID.randomUUID().toString().substring(0,8);auction.set("listings."+id+".seller",p.getUniqueId().toString());auction.set("listings."+id+".sellerName",p.getName());auction.set("listings."+id+".price",price);auction.set("listings."+id+".item",hand.clone());auction.set("listings."+id+".expires",System.currentTimeMillis()+getConfig().getLong("auction.duration-hours",48)*3600000L);hand.setAmount(0);save(auction,ahFile);p.sendMessage(ChatColor.GREEN+"Angebot erstellt. ID: "+id);}
    private void buyAuction(Player p,String id){String path="listings."+id;if(!auction.contains(path))return;String seller=auction.getString(path+".seller");if(p.getUniqueId().toString().equals(seller)){p.sendMessage(ChatColor.RED+"Du kannst dein eigenes Angebot nicht kaufen.");return;}if(auction.getLong(path+".expires")<System.currentTimeMillis()){p.sendMessage(ChatColor.RED+"Dieses Angebot ist abgelaufen.");return;}double price=auction.getDouble(path+".price");ItemStack item=auction.getItemStack(path+".item");if(item==null||p.getInventory().firstEmpty()<0){p.sendMessage(ChatColor.RED+"Dein Inventar ist voll oder das Angebot ungültig.");return;}if(!withdraw(p.getUniqueId(),price)){p.sendMessage(ChatColor.RED+"Nicht genug Geld.");return;}if(!p.getInventory().addItem(item).isEmpty()){deposit(p.getUniqueId(),price);p.sendMessage(ChatColor.RED+"Kauf fehlgeschlagen; Geld wurde erstattet.");return;}try{UUID sellerId=UUID.fromString(seller);deposit(sellerId,price);}catch(Exception ignored){}auction.set(path,null);save(auction,ahFile);p.sendMessage(ChatColor.GREEN+"Gekauft für "+format(price));}

    @EventHandler public void onInventoryClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(!title.startsWith(ChatColor.DARK_AQUA.toString()))return;e.setCancelled(true);if(e.getClickedInventory()==null||e.getClickedInventory()!=e.getView().getTopInventory())return;int slot=e.getRawSlot();
        if(title.contains("Shop")){ItemStack clicked=e.getCurrentItem();if(clicked==null||clicked.getType()==Material.GRAY_STAINED_GLASS_PANE)return;String key=clicked.getType().name();YamlConfiguration shop=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"shop.yml"));double price=shop.getDouble("items."+key+(e.isLeftClick()?".buy":".sell"));if(price<=0){p.sendMessage(ChatColor.RED+"Dieser Handel ist deaktiviert.");return;}if(e.isLeftClick()){if(!withdraw(p.getUniqueId(),price)){p.sendMessage(ChatColor.RED+"Nicht genug Geld.");return;}Map<Integer,ItemStack> leftovers=p.getInventory().addItem(new ItemStack(clicked.getType(),1));if(!leftovers.isEmpty()){deposit(p.getUniqueId(),price);p.sendMessage(ChatColor.RED+"Inventar voll.");}else p.sendMessage(ChatColor.GREEN+"Gekauft für "+format(price));}else{int amount=removeMaterial(p,clicked.getType(),1);if(amount>0){deposit(p.getUniqueId(),price);p.sendMessage(ChatColor.GREEN+"Verkauft für "+format(price));}}}
        else if(title.contains("Aufträge")){YamlConfiguration y=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"orders.yml"));ConfigurationSection s=y.getConfigurationSection("orders");if(s==null)return;int idx=slot-10;if(idx<0)return;List<String> ids=new ArrayList<>(s.getKeys(false));if(idx>=ids.size())return;String id=ids.get(idx);activeOrder.put(p.getUniqueId(),id);String path="orders."+id;Material m=Material.matchMaterial(y.getString(path+".material","STONE"));int need=y.getInt(path+".amount");if(m==null)return;if(countMaterial(p,m)>=need){removeMaterial(p,m,need);double reward=y.getDouble(path+".reward");deposit(p.getUniqueId(),reward);p.sendMessage(ChatColor.GREEN+"Auftrag abgeschlossen: +"+format(reward));activeOrder.remove(p.getUniqueId());}else p.sendMessage(ChatColor.YELLOW+"Dir fehlen noch Items. Benötigt: "+need+" "+m.name()+". Öffne /orders erneut, wenn du genug hast.");}
        else if(title.contains("Custom Items")){ItemStack it=e.getCurrentItem();String id=customId(it);if(id!=null){ItemSpec spec=specs.get(id);p.sendMessage(ChatColor.LIGHT_PURPLE+spec.title+ChatColor.GRAY+" – "+spec.rarity+" – "+abilityName(spec.ability));}}
        else if(title.contains("Auktionshaus")){ItemStack it=e.getCurrentItem();if(it==null||it.getType()==Material.GRAY_STAINED_GLASS_PANE)return;ItemMeta meta=it.getItemMeta();if(meta==null||meta.getLore()==null)return;for(String line:meta.getLore())if(ChatColor.stripColor(line).startsWith("Listing-ID:")){String id=ChatColor.stripColor(line).replace("Listing-ID:","").trim();buyAuction(p,id);openAh(p);break;}}
    }
    private int countMaterial(Player p,Material m){int total=0;for(ItemStack it:p.getInventory().getContents())if(it!=null&&it.getType()==m&&customId(it)==null)total+=it.getAmount();return total;}
    private int removeMaterial(Player p,Material m,int wanted){int left=wanted;for(int i=0;i<p.getInventory().getSize()&&left>0;i++){ItemStack it=p.getInventory().getItem(i);if(it!=null&&it.getType()==m&&customId(it)==null){int take=Math.min(left,it.getAmount());it.setAmount(it.getAmount()-take);left-=take;}}return wanted-left;}
    @EventHandler public void onProjectileHit(ProjectileHitEvent e){
        if(e.getEntity() instanceof Arrow arrow && arrow.hasMetadata("us_explosive")){
            arrow.getWorld().createExplosion(arrow.getLocation(),1.6f,false,false,arrow.getShooter() instanceof Entity shooter?shooter:null);
            arrow.remove();
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e){BukkitTask t=pendingRtp.remove(e.getPlayer().getUniqueId());if(t!=null)t.cancel();}

    @EventHandler public void onInteract(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND)return;ItemStack item=e.getItem();String id=customId(item);if(id==null)return;ItemSpec s=specs.get(id);if(s==null||s.ability.equals("material")||s.ability.equals("key"))return;if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;e.setCancelled(true);Player p=e.getPlayer();long now=System.currentTimeMillis(),ready=abilityCooldown.getOrDefault(p.getUniqueId(),0L);if(ready>now){p.sendMessage(ChatColor.RED+"Cooldown: "+((ready-now+999)/1000)+"s");return;}abilityCooldown.put(p.getUniqueId(),now+cooldown(id)*1000L);
        switch(s.ability){case "wither"->{WitherSkull skull=p.launchProjectile(WitherSkull.class);skull.setIsIncendiary(false);skull.setYield(0);}
            case "dragon","dragon_blade"->{for(Entity ent:p.getNearbyEntities(6,4,6))if(ent instanceof LivingEntity le&&ent!=p){le.damage(id.equals("dragon")?8:6,p);le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.2).setY(.45));}p.getWorld().playSound(p.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,.7f,1.4f);}
            case "crystal","heart"->{p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1));p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().add(0,1,0),35,.8,.8,.8,.05);}
            case "wind"->{p.setVelocity(p.getLocation().getDirection().multiply(1.8).setY(.8));p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_WIND_BURST,1,1);}
            case "shadow_spear","storm"->{for(Entity ent:p.getNearbyEntities(12,6,12))if(ent instanceof LivingEntity le&&ent!=p){le.damage(7,p);if(id.equals("storm"))le.getWorld().strikeLightningEffect(le.getLocation());}}
            case "titan"->{p.setVelocity(p.getLocation().getDirection().multiply(1.3).setY(1.0));p.getWorld().playSound(p.getLocation(),Sound.ITEM_MACE_SMASH_GROUND,1,.8f);}
            case "void"->{p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,60,0));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,1));}
            case "chaos"->{for(Entity ent:p.getNearbyEntities(5,3,5))if(ent instanceof LivingEntity le&&ent!=p){le.damage(5,p);le.setVelocity(le.getVelocity().add(new org.bukkit.util.Vector(random.nextDouble()-.5,.7,random.nextDouble()-.5)));}}
            case "phantom"->{Arrow ar=p.launchProjectile(Arrow.class);ar.setDamage(6);ar.setVelocity(p.getLocation().getDirection().multiply(3));}
            case "inferno","fireball"->{Fireball fb=p.launchProjectile(Fireball.class);fb.setIsIncendiary(false);fb.setYield(0);}
            case "totem"->{p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,200,2));p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1));}
            case "eye"->{p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,400,0));p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,100,0));}
            case "explosive_arrow"->{Arrow ar=p.launchProjectile(Arrow.class);ar.setMetadata("us_explosive",new org.bukkit.metadata.FixedMetadataValue(this,true));}
            case "tnt"->{p.getWorld().spawn(p.getLocation().add(p.getLocation().getDirection().multiply(2)),TNTPrimed.class,t->t.setFuseTicks(50));}
            case "pearl"->{p.launchProjectile(EnderPearl.class);}
        }
        if(item!=null&&item.getType().getMaxStackSize()==1&&id.equals("orbital_totem")){item.setAmount(item.getAmount()-1);}p.sendMessage(ChatColor.GREEN+"Fähigkeit aktiviert: "+s.title);
    }
    private int cooldown(String id){return switch(id){case "orbital_wither_skull","dragon_orbital","dragon_blade","custom_tnt"->45;case "orbital_totem","orbital_heart"->90;case "orbital_ender_pearl"->20;default->25;};}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(c.getName().equalsIgnoreCase("us")&&args.length==1)return List.of("reload","money","item","ah","rtp");if(c.getName().equalsIgnoreCase("ah")&&args.length==1)return List.of("sell","listings");return Collections.emptyList();}
}
