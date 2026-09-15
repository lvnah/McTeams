package fr.mcteams;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class McTeamsPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private Location spawnLocation;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, TeamData> teams = new HashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final Map<UUID, String> playerRanks = new HashMap<>();
    private final Map<UUID, String> playerLangs = new HashMap<>();
    private final Map<UUID, Long> combatTag = new HashMap<>();
    private final Map<UUID, String> activeTeleports = new HashMap<>();
    private final Map<UUID, Boolean> spawnProtected = new HashMap<>();
    private final List<MarketItem> market = new ArrayList<>();

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadData();

        String[] cmds = {"setspawn", "spawn", "go", "team", "deposit", "balance", "bal", "sell", "buy", "buyview", "setrank", "lang", "gm"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            setupPlayerScoreboard(p);
            updatePlayerDisplayNameAndTab(p);
            spawnProtected.putIfAbsent(p.getUniqueId(), true);
            playerLangs.putIfAbsent(p.getUniqueId(), "en");
        }

        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 0L, 20L);
        getLogger().info("McTeams enabled and loaded successfully!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private String getMsg(Player p, String key, Object... args) {
        String lang = playerLangs.getOrDefault(p.getUniqueId(), "en");
        Map<String, String> dict = getDictionary(lang);
        String msg = dict.getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    private Map<String, String> getDictionary(String lang) {
        Map<String, String> map = new HashMap<>();
        if (lang.equals("fr")) {
            map.put("spawn_set", "§6Spawn défini et sauvegardé avec succès !");
            map.put("spawn_not_set", "§6Le spawn n'a pas été défini par un admin.");
            map.put("spawn_no_gold", "§6Le /spawn coûte §f3 Or§6. Tu n'en as pas assez, rejoins le spawn à pied en §f0 0§6 !");
            map.put("in_combat_tp", "§6Impossible de se téléporter en combat tag !");
            map.put("tp_started", "§6Téléportation au {0} dans {1} secondes... Ne bouge pas !");
            map.put("tp_cancelled_move", "§6Téléportation annulée (mouvement détecté).");
            map.put("tp_cancelled_combat", "§6Téléportation annulée (attaqué en combat).");
            map.put("tp_success", "§6Téléportation effectuée avec succès !");
            map.put("spawn_protected_msg", "§6Téléportation effectuée au spawn avec §aprotection §6!");
            map.put("rank_updated", "§6Grade de {0} défini sur : {1}");
            map.put("rank_target_update", "§6Ton grade a été mis à jour : {0}");
            map.put("go_limit", "§6Tu as déjà atteint la limite de 3 /go !");
            map.put("go_set", "§6Go '{0}' défini et sauvegardé !");
            map.put("go_not_found", "§6Ce go n'existe pas.");
            map.put("go_deleted", "§6Le go '{0}' a été supprimé.");
            map.put("go_list_empty", "§6Tu n'as aucun /go enregistré.");
            map.put("go_list", "§6Tes /go ({0}/3) : §f{1}");
            map.put("deposit_success", "§6Tu as déposé §f{0} lingots d'or. §6Nouveau solde: §f{1}");
            map.put("deposit_empty", "§6Tu n'as pas d'or dans ton inventaire !");
            map.put("balance_msg", "§6Ton solde est de : §f{0} Or");
            map.put("sell_usage", "§6Usage: §f/sell <id> <quantité> <prix>");
            map.put("sell_not_enough", "§6Tu n'as pas assez de cet item dans ton inventaire !");
            map.put("sell_success", "§6Item mis en vente sous l'ID §f{0} §6pour §f{1} Or l'unité §6!");
            map.put("buy_usage", "§6Usage: §f/buy <id> <quantité>");
            map.put("buy_not_found", "§6Cet ID n'existe pas sur le marché.");
            map.put("buy_success", "§6Achat réussi !");
            map.put("buy_no_money", "§6Tu n'as pas assez d'or !");
            map.put("too_close_spawn", "§6Impossible de placer un point à moins de 200 blocs du spawn !");
            map.put("team_already", "§6Tu es déjà dans une team.");
            map.put("team_created", "§6Team {0} créée et sauvegardée !");
            map.put("hq_set", "§6HQ de la team défini et sauvegardé !");
            map.put("hq_none", "§6Ta team n'a pas de HQ.");
            map.put("team_not_in", "§6Tu n'as pas de team.");
            map.put("team_kick", "§6Le joueur {0} a été expulsé.");
            map.put("combat_death", "§6[Combat] §f{0} s'est déconnecté en combat et a été tué !");
            map.put("death_respawn", "§6Tu es mort et as été réinitialisé au §fspawn §6avec protection !");
            map.put("block_break_deny", "§6Impossible de casser des blocs dans la zone protégée du spawn !");
            map.put("block_place_deny", "§6Impossible de poser des blocs dans la zone protégée du spawn !");
            map.put("damage_deny", "§6Impossible de frapper : cible ou attaquant sous protection du spawn !");
            map.put("lang_changed", "§6Langue changée en : §fFrançais");
        } else if (lang.equals("es")) {
            map.put("spawn_set", "§6¡Spawn definido y guardado con éxito!");
            map.put("spawn_not_set", "§6El spawn no ha sido definido por un administrador.");
            map.put("spawn_no_gold", "§6El /spawn cuesta §f3 de Oro§6. ¡No tienes suficiente, ve al spawn a pie en §f0 0§6!");
            map.put("in_combat_tp", "§6¡No puedes teletransportarte en combat tag!");
            map.put("tp_started", "§6Teletransportando a {0} en {1} segundos... ¡No te muevas!");
            map.put("tp_cancelled_move", "§6Teletransportación cancelada (movimiento detectado).");
            map.put("tp_cancelled_combat", "§6Teletransportación cancelada (atacado en combate).");
            map.put("tp_success", "§6¡Teletransportación exitosa!");
            map.put("spawn_protected_msg", "§6¡Teletransportado al spawn con §aprotección §6!");
            map.put("rank_updated", "§6Rango de {0} establecido en: {1}");
            map.put("rank_target_update", "§6Tu rango ha sido actualizado: {0}");
            map.put("go_limit", "§6¡Ya has alcanzado el límite de 3 /go!");
            map.put("go_set", "§6¡Go '{0}' definido y guardado!");
            map.put("go_not_found", "§6Este go no existe.");
            map.put("go_deleted", "§6El go '{0}' ha sido eliminado.");
            map.put("go_list_empty", "§6No tienes ningún /go guardado.");
            map.put("go_list", "§6Tus /go ({0}/3) : §f{1}");
            map.put("deposit_success", "§6Has depositado §f{0} lingotes de oro. §6Nuevo saldo: §f{1}");
            map.put("deposit_empty", "§6¡No tienes oro en tu inventario!");
            map.put("balance_msg", "§6Tu saldo es de: §f{0} Oro");
            map.put("sell_usage", "§6Uso: §f/sell <id> <cantidad> <precio>");
            map.put("sell_not_enough", "§6¡No tienes suficiente de este objeto en tu inventario!");
            map.put("sell_success", "§6¡Objeto puesto a la venta con ID §f{0} §6por §f{1} Oro c/u §6!");
            map.put("buy_usage", "§6Uso: §f/buy <id> <cantidad>");
            map.put("buy_not_found", "§6Este ID no existe en el mercado.");
            map.put("buy_success", "§6¡Compra exitosa!");
            map.put("buy_no_money", "§6¡No tienes suficiente oro!");
            map.put("too_close_spawn", "§6¡No puedes establecer un punto a menos de 200 bloques del spawn!");
            map.put("team_already", "§6Ya estás en un team.");
            map.put("team_created", "§6¡Team {0} creado y guardado!");
            map.put("hq_set", "§6¡HQ del team definido y guardado!");
            map.put("hq_none", "§6Tu team no tiene HQ.");
            map.put("team_not_in", "§6No estás en ningún team.");
            map.put("team_kick", "§6El jugador {0} ha sido expulsado.");
            map.put("combat_death", "§6[Combat] §f{0} se desconectó en combate y murió!");
            map.put("death_respawn", "§6¡Has muerto y reaparecido en el §fspawn §6con protección!");
            map.put("block_break_deny", "§6¡No puedes romper bloques en la zona protegida del spawn!");
            map.put("block_place_deny", "§6¡No puedes colocar bloques en la zona protegida del spawn!");
            map.put("damage_deny", "§6¡Imposible golpear: objetivo o atacante bajo protección del spawn!");
            map.put("lang_changed", "§6Idioma cambiado a: §fEspañol");
        } else {
            map.put("spawn_set", "§6Spawn defined and saved successfully!");
            map.put("spawn_not_set", "§6Spawn has not been set by an admin.");
            map.put("spawn_no_gold", "§6/spawn costs §f3 Gold§6. You don't have enough, walk to spawn at §f0 0§6!");
            map.put("in_combat_tp", "§6Cannot teleport while in combat tag!");
            map.put("tp_started", "§6Teleporting to {0} in {1} seconds... Don't move!");
            map.put("tp_cancelled_move", "§6Teleportation cancelled (movement detected).");
            map.put("tp_cancelled_combat", "§6Teleportation cancelled (attacked in combat).");
            map.put("tp_success", "§6Teleportation successful!");
            map.put("spawn_protected_msg", "§6Teleported to spawn with §aprotection§6!");
            map.put("rank_updated", "§6Rank of {0} set to: {1}");
            map.put("rank_target_update", "§6Your rank has been updated: {0}");
            map.put("go_limit", "§6You have reached the limit of 3 /go!");
            map.put("go_set", "§6Go '{0}' defined and saved!");
            map.put("go_not_found", "§6This go does not exist.");
            map.put("go_deleted", "§6The go '{0}' has been deleted.");
            map.put("go_list_empty", "§6You have no saved /go.");
            map.put("go_list", "§6Your /go ({0}/3) : §f{1}");
            map.put("deposit_success", "§6You deposited §f{0} gold ingots. §6New balance: §f{1}");
            map.put("deposit_empty", "§6You don't have any gold in your inventory!");
            map.put("balance_msg", "§6Your balance is: §f{0} Gold");
            map.put("sell_usage", "§6Usage: §f/sell <id> <quantity> <price>");
            map.put("sell_not_enough", "§6You don't have enough of this item in your inventory!");
            map.put("sell_success", "§6Item listed for sale with ID §f{0} §6for §f{1} Gold each§6!");
            map.put("buy_usage", "§6Usage: §f/buy <id> <quantity>");
            map.put("buy_not_found", "§6This ID does not exist in the market.");
            map.put("buy_success", "§6Purchase successful!");
            map.put("buy_no_money", "§6You don't have enough gold!");
            map.put("too_close_spawn", "§6Cannot set a point within 200 blocks of spawn!");
            map.put("team_already", "§6You are already in a team.");
            map.put("team_created", "§6Team {0} created and saved!");
            map.put("hq_set", "§6Team HQ defined and saved!");
            map.put("hq_none", "§6Your team has no HQ.");
            map.put("team_not_in", "§6You are not in a team.");
            map.put("team_kick", "§6Player {0} has been kicked.");
            map.put("combat_death", "§6[Combat] §f{0} disconnected in combat and was killed!");
            map.put("death_respawn", "§6You died and were reset to the §fspawn §6with protection!");
            map.put("block_break_deny", "§6You cannot break blocks in the protected spawn area!");
            map.put("block_place_deny", "§6You cannot place blocks in the protected spawn area!");
            map.put("damage_deny", "§6Cannot hit: target or attacker under spawn protection!");
            map.put("lang_changed", "§6Language changed to: §fEnglish");
        }
        return map;
    }

    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] parts = s.split(";");
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) return null;
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(w, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveData() {
        getConfig().set("spawn", serializeLocation(spawnLocation));

        getConfig().set("balances", null);
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            getConfig().set("balances." + entry.getKey().toString(), entry.getValue());
        }

        getConfig().set("ranks", null);
        for (Map.Entry<UUID, String> entry : playerRanks.entrySet()) {
            getConfig().set("ranks." + entry.getKey().toString(), entry.getValue());
        }

        getConfig().set("langs", null);
        for (Map.Entry<UUID, String> entry : playerLangs.entrySet()) {
            getConfig().set("langs." + entry.getKey().toString(), entry.getValue());
        }

        getConfig().set("homes", null);
        for (Map.Entry<UUID, Map<String, Location>> entry : playerHomes.entrySet()) {
            for (Map.Entry<String, Location> home : entry.getValue().entrySet()) {
                getConfig().set("homes." + entry.getKey().toString() + "." + home.getKey(), serializeLocation(home.getValue()));
            }
        }

        getConfig().set("teams", null);
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey();
            TeamData t = entry.getValue();
            getConfig().set(path + ".creator", t.creator.toString());
            getConfig().set(path + ".creatorName", t.creatorName);
            getConfig().set(path + ".members", t.members);
            getConfig().set(path + ".hq", serializeLocation(t.hq));
        }

        saveConfig();
    }

    private void loadData() {
        spawnLocation = deserializeLocation(getConfig().getString("spawn"));

        ConfigurationSection balSec = getConfig().getConfigurationSection("balances");
        if (balSec != null) {
            for (String key : balSec.getKeys(false)) {
                balances.put(UUID.fromString(key), balSec.getDouble(key));
            }
        }

        ConfigurationSection rankSec = getConfig().getConfigurationSection("ranks");
        if (rankSec != null) {
            for (String key : rankSec.getKeys(false)) {
                playerRanks.put(UUID.fromString(key), rankSec.getString(key));
            }
        }

        ConfigurationSection langSec = getConfig().getConfigurationSection("langs");
        if (langSec != null) {
            for (String key : langSec.getKeys(false)) {
                playerLangs.put(UUID.fromString(key), langSec.getString(key));
            }
        }

        ConfigurationSection homeSec = getConfig().getConfigurationSection("homes");
        if (homeSec != null) {
            for (String uuidStr : homeSec.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Location> homes = new HashMap<>();
                ConfigurationSection playerHomeSec = homeSec.getConfigurationSection(uuidStr);
                if (playerHomeSec != null) {
                    for (String homeName : playerHomeSec.getKeys(false)) {
                        Location loc = deserializeLocation(playerHomeSec.getString(homeName));
                        if (loc != null) homes.put(homeName, loc);
                    }
                }
                playerHomes.put(uuid, homes);
            }
        }

        ConfigurationSection teamSec = getConfig().getConfigurationSection("teams");
        if (teamSec != null) {
            for (String teamName : teamSec.getKeys(false)) {
                String path = "teams." + teamName;
                UUID creator = UUID.fromString(getConfig().getString(path + ".creator"));
                String creatorName = getConfig().getString(path + ".creatorName");
                List<String> members = getConfig().getStringList(path + ".members");
                Location hq = deserializeLocation(getConfig().getString(path + ".hq"));

                TeamData t = new TeamData(teamName, creatorName, creator);
                t.members = members;
                t.hq = hq;
                teams.put(teamName, t);

                playerTeam.put(creator, teamName);
                for (String mName : members) {
                    Player mPlayer = Bukkit.getPlayer(mName);
                    if (mPlayer != null) {
                        playerTeam.put(mPlayer.getUniqueId(), teamName);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        playerRanks.putIfAbsent(p.getUniqueId(), "default");
        playerLangs.putIfAbsent(p.getUniqueId(), "en");
        spawnProtected.put(p.getUniqueId(), true);
        setupPlayerScoreboard(p);
        updatePlayerDisplayNameAndTab(p);
        if (spawnLocation != null) {
            p.teleport(spawnLocation);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isInCombat(p)) {
            p.setHealth(0.0);
            Bukkit.broadcastMessage(getMsg(p, "combat_death", p.getName()));
        }
        combatTag.remove(p.getUniqueId());
        activeTeleports.remove(p.getUniqueId());
        spawnProtected.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        combatTag.remove(p.getUniqueId());
        activeTeleports.remove(p.getUniqueId());
        spawnProtected.put(p.getUniqueId(), true);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (spawnLocation != null) {
                p.spigot().respawn();
                p.teleport(spawnLocation);
                p.sendMessage(getMsg(p, "death_respawn"));
            }
        }, 2L);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (spawnProtected.getOrDefault(p.getUniqueId(), false)) {
                e.setCancelled(true);
                p.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (spawnProtected.getOrDefault(p.getUniqueId(), false) && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.isOp()) return;
        if (isInSpawnRegion(p) || (spawnLocation != null && p.getLocation().distanceSquared(spawnLocation) <= 2500)) {
            e.setCancelled(true);
            p.sendMessage(getMsg(p, "block_break_deny"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.isOp()) return;
        if (isInSpawnRegion(p) || (spawnLocation != null && p.getLocation().distanceSquared(spawnLocation) <= 2500)) {
            e.setCancelled(true);
            p.sendMessage(getMsg(p, "block_place_deny"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            Player victim = (Player) e.getEntity();
            Player attacker = (Player) e.getDamager();

            if (spawnProtected.getOrDefault(victim.getUniqueId(), false) || spawnProtected.getOrDefault(attacker.getUniqueId(), false)) {
                e.setCancelled(true);
                attacker.sendMessage(getMsg(attacker, "damage_deny"));
                return;
            }

            long expireTime = System.currentTimeMillis() + 45000L;
            combatTag.put(victim.getUniqueId(), expireTime);
            combatTag.put(attacker.getUniqueId(), expireTime);
        }
    }

    private boolean isInCombat(Player p) {
        UUID uuid = p.getUniqueId();
        if (!combatTag.containsKey(uuid)) return false;
        if (System.currentTimeMillis() > combatTag.get(uuid)) {
            combatTag.remove(uuid);
            return false;
        }
        return true;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String rank = playerRanks.getOrDefault(p.getUniqueId(), "default");
        String colorCode = getRankColorCode(rank);
        
        String clan = playerTeam.get(p.getUniqueId());
        String clanTag = (clan != null) ? "§f[" + clan + "] " : "";
        
        e.setFormat(clanTag + colorCode + p.getName() + " §7> §f" + e.getMessage());
    }

    private String getRankColorCode(String rank) {
        switch (rank.toLowerCase()) {
            case "owner": return "§4";
            case "mod": case "moderator": return "§b";
            case "elite": return "§b";
            case "helper": return "§e";
            case "vip": return "§6";
            default: return "§f";
        }
    }

    private void updatePlayerDisplayNameAndTab(Player p) {
        String rank = playerRanks.getOrDefault(p.getUniqueId(), "default");
        String colorCode = getRankColorCode(rank);
        String clan = playerTeam.get(p.getUniqueId());
        String clanTag = (clan != null) ? "§f[" + clan + "] " : "";

        String formattedName = clanTag + colorCode + p.getName();
        p.setPlayerListName(formattedName);
        p.setCustomName(formattedName);
        p.setCustomNameVisible(true);
    }

    private void setupPlayerScoreboard(Player target) {
        Scoreboard board = target.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            target.setScoreboard(board);
        }
        createRankTeamInBoard(board, "01owner", "§4");
        createRankTeamInBoard(board, "02mod", "§b");
        createRankTeamInBoard(board, "03elite", "§b");
        createRankTeamInBoard(board, "04helper", "§e");
        createRankTeamInBoard(board, "05vip", "§6");
        createRankTeamInBoard(board, "06default", "§f");

        for (Player p : Bukkit.getOnlinePlayers()) {
            assignPlayerToRankTeam(p.getScoreboard(), target);
        }
    }

    private void createRankTeamInBoard(Scoreboard board, String teamName, String prefix) {
        Team t = board.getTeam(teamName);
        if (t == null) {
            t = board.registerNewTeam(teamName);
        }
        t.setPrefix(prefix);
        t.setSuffix("");
    }

    private void assignPlayerToRankTeam(Scoreboard board, Player target) {
        String rank = playerRanks.getOrDefault(target.getUniqueId(), "default").toLowerCase();
        String teamKey = "06default";
        if (rank.equals("owner")) teamKey = "01owner";
        else if (rank.equals("mod") || rank.equals("moderator")) teamKey = "02mod";
        else if (rank.equals("elite")) teamKey = "03elite";
        else if (rank.equals("helper")) teamKey = "04helper";
        else if (rank.equals("vip")) teamKey = "05vip";

        for (Team t : board.getTeams()) {
            if (t.hasEntry(target.getName())) {
                t.removeEntry(target.getName());
            }
        }
        Team t = board.getTeam(teamKey);
        if (t != null) {
            t.addEntry(target.getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        UUID uuid = p.getUniqueId();

        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "gm":
                if (!p.isOp()) {
                    p.sendMessage("§cYou do not have permission.");
                    return true;
                }
                if (args.length != 1) {
                    p.sendMessage("§6Usage: §f/gm <0|1>");
                    return true;
                }
                if (args[0].equals("0")) {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.sendMessage("§6GameMode set to §fSurvival");
                } else if (args[0].equals("1")) {
                    p.setGameMode(GameMode.CREATIVE);
                    p.sendMessage("§6GameMode set to §fCreative");
                } else {
                    p.sendMessage("§6Usage: §f/gm <0|1>");
                }
                break;

            case "lang":
                if (args.length != 1) {
                    p.sendMessage("§6Usage: §f/lang <en|fr|es>");
                    return true;
                }
                String lang = args[0].toLowerCase();
                if (!Arrays.asList("en", "fr", "es").contains(lang)) {
                    p.sendMessage("§6Languages: §fen, fr, es");
                    return true;
                }
                playerLangs.put(uuid, lang);
                saveData();
                p.sendMessage(getMsg(p, "lang_changed"));
                break;

            case "setspawn":
                if (!p.isOp()) return true;
                spawnLocation = p.getLocation();
                saveData();
                p.sendMessage(getMsg(p, "spawn_set"));
                break;

            case "spawn":
                if (spawnLocation == null) {
                    p.sendMessage(getMsg(p, "spawn_not_set"));
                    return true;
                }
                if (isInCombat(p)) {
                    p.sendMessage(getMsg(p, "in_combat_tp"));
                    return true;
                }
                double currentBal = balances.getOrDefault(uuid, 0.0);
                if (currentBal < 3.0) {
                    p.sendMessage(getMsg(p, "spawn_no_gold"));
                    return true;
                }
                balances.put(uuid, currentBal - 3.0);
                saveData();
                startTeleportation(p, spawnLocation, "Spawn", 15, true);
                break;

            case "setrank":
                if (!p.isOp()) {
                    p.sendMessage("§6You do not have permission.");
                    return true;
                }
                if (args.length != 2) {
                    p.sendMessage("§6Usage: §f/setrank <player> <default|vip|helper|elite|mod|owner>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage("§6Player not found.");
                    return true;
                }
                String newRank = args[1].toLowerCase();
                if (!Arrays.asList("default", "vip", "helper", "elite", "mod", "owner").contains(newRank)) {
                    p.sendMessage("§6Valid ranks: default, vip, helper, elite, mod, owner");
                    return true;
                }
                playerRanks.put(target.getUniqueId(), newRank);
                saveData();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    assignPlayerToRankTeam(online.getScoreboard(), target);
                }
                updatePlayerDisplayNameAndTab(target);
                p.sendMessage(getMsg(p, "rank_updated", target.getName(), newRank));
                target.sendMessage(getMsg(target, "rank_target_update", newRank));
                break;

            case "go":
                if (isInCombat(p)) {
                    p.sendMessage(getMsg(p, "in_combat_tp"));
                    return true;
                }
                if (args.length == 0) {
                    p.sendMessage("§6Usage: §f/go set <name> | /go <name> | /go delete <name> | /go list");
                    return true;
                }
                Map<String, Location> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());
                if (args[0].equalsIgnoreCase("list")) {
                    if (homes.isEmpty()) {
                        p.sendMessage(getMsg(p, "go_list_empty"));
                    } else {
                        p.sendMessage(getMsg(p, "go_list", homes.size(), String.join(", ", homes.keySet())));
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("delete") && args.length == 2) {
                    String hName = args[1].toLowerCase();
                    if (homes.remove(hName) != null) {
                        saveData();
                        p.sendMessage(getMsg(p, "go_deleted", hName));
                    } else {
                        p.sendMessage(getMsg(p, "go_not_found"));
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("set") && args.length == 2) {
                    if (spawnLocation != null && p.getLocation().distanceSquared(spawnLocation) < 40000) { // 200 blocs
                        p.sendMessage(getMsg(p, "too_close_spawn"));
                        return true;
                    }
                    if (homes.size() >= 3) {
                        p.sendMessage(getMsg(p, "go_limit"));
                        return true;
                    }
                    homes.put(args[1].toLowerCase(), p.getLocation());
                    saveData();
                    p.sendMessage(getMsg(p, "go_set", args[1]));
                } else {
                    String homeName = args[0].toLowerCase();
                    if (homes.containsKey(homeName)) {
                        int delay = isPlayerNearby(p, 30) ? 5 : 0;
                        startTeleportation(p, homes.get(homeName), "Go: " + homeName, delay, false);
                    } else {
                        p.sendMessage(getMsg(p, "go_not_found"));
                    }
                }
                break;

            case "team":
                if (args.length == 0) {
                    p.sendMessage("§6Usage: §f/team <create|invite|leave|kick|sethq|hq|info>");
                    return true;
                }
                handleTeamCommand(p, args);
                break;

            case "deposit":
                int goldCount = 0;
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack item = p.getInventory().getItem(i);
                    if (item != null && item.getType() == Material.GOLD_INGOT) {
                        goldCount += item.getAmount();
                        p.getInventory().setItem(i, null);
                    }
                }
                if (goldCount > 0) {
                    balances.put(uuid, balances.getOrDefault(uuid, 0.0) + goldCount);
                    saveData();
                    p.sendMessage(getMsg(p, "deposit_success", goldCount, balances.get(uuid)));
                } else {
                    p.sendMessage(getMsg(p, "deposit_empty"));
                }
                break;

            case "balance":
            case "bal":
                p.sendMessage(getMsg(p, "balance_msg", balances.getOrDefault(uuid, 0.0)));
                break;

            case "sell":
                if (args.length != 3) {
                    p.sendMessage(getMsg(p, "sell_usage"));
                    return true;
                }
                try {
                    String sellId = args[0];
                    int qty = Integer.parseInt(args[1]);
                    double price = Double.parseDouble(args[2]);

                    ItemStack inHand = p.getItemInHand();
                    if (inHand == null || inHand.getType() == Material.AIR || inHand.getAmount() < qty) {
                        p.sendMessage(getMsg(p, "sell_not_enough"));
                        return true;
                    }
                    
                    inHand.setAmount(inHand.getAmount() - qty);
                    if (inHand.getAmount() <= 0) {
                        p.setItemInHand(null);
                    }
                    
                    ItemStack toSell = inHand.clone();
                    toSell.setAmount(qty);
                    
                    market.add(new MarketItem(sellId, p.getName(), uuid, toSell, price));
                    p.sendMessage(getMsg(p, "sell_success", sellId, price));
                } catch (NumberFormatException e) {
                    p.sendMessage("§6Invalid numbers provided.");
                }
                break;
                
            case "buyview":
                p.sendMessage("§6--- Market ---");
                for (MarketItem item : market) {
                    p.sendMessage("§6ID: §f" + item.id + " §6| §f" + item.item.getAmount() + "x " + item.item.getType().name().toLowerCase() + " §6| Price (each): §f" + item.price + " Gold §6| Seller: §f" + item.seller);
                }
                break;

            case "buy":
                if (args.length != 2) {
                    p.sendMessage(getMsg(p, "buy_usage"));
                    return true;
                }
                try {
                    String buyId = args[0];
                    int buyQty = Integer.parseInt(args[1]);

                    MarketItem toBuy = market.stream().filter(m -> m.id.equalsIgnoreCase(buyId)).findFirst().orElse(null);
                    if (toBuy == null) {
                        p.sendMessage(getMsg(p, "buy_not_found"));
                        return true;
                    }

                    double totalPrice = toBuy.price * buyQty;
                    double buyerBalance = balances.getOrDefault(uuid, 0.0);

                    if (buyerBalance >= totalPrice) {
                        balances.put(uuid, buyerBalance - totalPrice);
                        balances.put(toBuy.sellerUuid, balances.getOrDefault(toBuy.sellerUuid, 0.0) + totalPrice);
                        saveData();
                        
                        ItemStack purchasedItem = toBuy.item.clone();
                        purchasedItem.setAmount(buyQty);
                        p.getInventory().addItem(purchasedItem);
                        
                        market.remove(toBuy);
                        p.sendMessage(getMsg(p, "buy_success"));
                        
                        Player sellerPlayer = Bukkit.getPlayer(toBuy.sellerUuid);
                        if (sellerPlayer != null) {
                            sellerPlayer.sendMessage("§6Your item (" + toBuy.id + ") was sold for §f" + totalPrice + " Gold§6!");
                        }
                    } else {
                        p.sendMessage(getMsg(p, "buy_no_money"));
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage("§6Invalid quantity.");
                }
                break;
        }
        return true;
    }

    private void startTeleportation(Player p, Location targetLoc, String name, int delaySeconds, boolean giveSpawnProtection) {
        if (delaySeconds <= 0) {
            p.teleport(targetLoc);
            if (giveSpawnProtection) {
                spawnProtected.put(p.getUniqueId(), true);
                p.sendMessage(getMsg(p, "spawn_protected_msg"));
            } else {
                p.sendMessage(getMsg(p, "tp_success"));
            }
            return;
        }

        p.sendMessage(getMsg(p, "tp_started", name, delaySeconds));
        Location locBefore = p.getLocation();
        
        new BukkitRunnable() {
            int countdown = delaySeconds;
            @Override
            public void run() {
                if (!p.isOnline()) {
                    activeTeleports.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (!p.getLocation().getWorld().equals(locBefore.getWorld()) || p.getLocation().distanceSquared(locBefore) > 0.5) {
                    p.sendMessage(getMsg(p, "tp_cancelled_move"));
                    activeTeleports.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (isInCombat(p)) {
                    p.sendMessage(getMsg(p, "tp_cancelled_combat"));
                    activeTeleports.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (countdown <= 0) {
                    p.teleport(targetLoc);
                    if (giveSpawnProtection) {
                        spawnProtected.put(p.getUniqueId(), true);
                        p.sendMessage(getMsg(p, "spawn_protected_msg"));
                    } else {
                        p.sendMessage(getMsg(p, "tp_success"));
                    }
                    activeTeleports.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                
                activeTeleports.put(p.getUniqueId(), countdown + "s");
                countdown--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void handleTeamCommand(Player p, String[] args) {
        String action = args[0].toLowerCase();
        UUID uuid = p.getUniqueId();
        String currentTeam = playerTeam.get(uuid);

        switch (action) {
            case "create":
                if (currentTeam != null) { p.sendMessage(getMsg(p, "team_already")); return; }
                if (args.length < 2) { p.sendMessage("§6Usage: §f/team create <name>"); return; }
                String teamName = args[1];
                if (teams.containsKey(teamName)) { p.sendMessage("§6This name is already taken."); return; }
                
                TeamData newTeam = new TeamData(teamName, p.getName(), uuid);
                teams.put(teamName, newTeam);
                playerTeam.put(uuid, teamName);
                saveData();
                updatePlayerDisplayNameAndTab(p);
                p.sendMessage(getMsg(p, "team_created", teamName));
                break;

            case "sethq":
                if (currentTeam == null) { p.sendMessage(getMsg(p, "team_not_in")); return; }
                TeamData t = teams.get(currentTeam);
                if (!t.creator.equals(uuid)) { p.sendMessage("§6Only the creator can do this."); return; }
                if (spawnLocation != null && p.getLocation().distanceSquared(spawnLocation) < 40000) { // 200 blocs
                    p.sendMessage(getMsg(p, "too_close_spawn"));
                    return;
                }
                t.hq = p.getLocation();
                saveData();
                p.sendMessage(getMsg(p, "hq_set"));
                break;

            case "hq":
                if (currentTeam == null) { p.sendMessage(getMsg(p, "team_not_in")); return; }
                if (isInCombat(p)) {
                    p.sendMessage(getMsg(p, "in_combat_tp"));
                    return;
                }
                Location hq = teams.get(currentTeam).hq;
                if (hq == null) {
                    p.sendMessage(getMsg(p, "hq_none"));
                } else {
                    int delay = isPlayerNearby(p, 30) ? 5 : 0;
                    startTeleportation(p, hq, "Team HQ", delay, false);
                }
                break;
                
            case "info":
                String targetTeam = (args.length > 1) ? args[1] : currentTeam;
                if (targetTeam == null || !teams.containsKey(targetTeam)) { p.sendMessage("§6Team not found."); return; }
                TeamData infoTeam = teams.get(targetTeam);
                p.sendMessage("§6--- Team: §f" + infoTeam.name + " §6---");
                p.sendMessage("§6Creator: §f" + infoTeam.creatorName);
                p.sendMessage("§6HQ: §f" + (infoTeam.hq != null ? "Set" : "Not set"));
                p.sendMessage("§6Members (" + infoTeam.members.size() + "): §f" + String.join(", ", infoTeam.members));
                break;
                
            case "leave":
                if (currentTeam == null) { p.sendMessage(getMsg(p, "team_not_in")); return; }
                TeamData lTeam = teams.get(currentTeam);
                if (lTeam.creator.equals(uuid)) {
                    if (lTeam.members.size() > 1) {
                        p.sendMessage("§6You cannot disband your team while members remain.");
                        return;
                    }
                    teams.remove(currentTeam);
                    playerTeam.remove(uuid);
                    saveData();
                    updatePlayerDisplayNameAndTab(p);
                    p.sendMessage("§6You dissolved your team.");
                } else {
                    lTeam.members.remove(p.getName());
                    playerTeam.remove(uuid);
                    saveData();
                    updatePlayerDisplayNameAndTab(p);
                    p.sendMessage("§6You left your team.");
                }
                break;

            case "kick":
                if (currentTeam == null) { p.sendMessage(getMsg(p, "team_not_in")); return; }
                TeamData kTeam = teams.get(currentTeam);
                if (!kTeam.creator.equals(uuid)) {
                    p.sendMessage("§6Only the creator can kick members.");
                    return;
                }
                if (args.length < 2) {
                    p.sendMessage("§6Usage: §f/team kick <player>");
                    return;
                }
                String targetName = args[1];
                if (!kTeam.members.contains(targetName)) {
                    p.sendMessage("§6This player is not in your team.");
                    return;
                }
                kTeam.members.remove(targetName);
                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer != null) {
                    playerTeam.remove(targetPlayer.getUniqueId());
                    updatePlayerDisplayNameAndTab(targetPlayer);
                    targetPlayer.sendMessage("§6You have been kicked from the team.");
                }
                saveData();
                p.sendMessage(getMsg(p, "team_kick", targetName));
                break;
        }
    }

    private boolean isPlayerNearby(Player p, double radius) {
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player && e != p) return true;
        }
        return false;
    }

    private void updateScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            
            if (spawnProtected.getOrDefault(uuid, false)) {
                if (!isInSpawnRegion(p)) {
                    spawnProtected.put(uuid, false);
                    p.sendMessage("§6You left the spawn zone and lost your §cprotection§6!");
                }
            }

            Scoreboard board = p.getScoreboard();
            Objective obj = board.getObjective("mcteams");
            if (obj == null) {
                obj = board.registerNewObjective("mcteams", "dummy");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                obj.setDisplayName("§6SoupTeams §f[Map 1]");
            }

            Map<String, Integer> currentLines = new LinkedHashMap<>();
            currentLines.put("§m---------------------", 6);
            
            String tName = playerTeam.getOrDefault(uuid, "None");
            if(tName.length() > 10) tName = tName.substring(0, 10) + "..";
            currentLines.put("§6Team: §f" + tName, 5);
            
            currentLines.put("§6Balance: §f" + balances.getOrDefault(uuid, 0.0), 4);
            
            boolean isProtected = spawnProtected.getOrDefault(uuid, false);
            currentLines.put("§6Spawn protection: " + (isProtected ? "§aEnable" : "§cDisable"), 3);

            if (activeTeleports.containsKey(uuid)) {
                currentLines.put("§6Teleportation: §f" + activeTeleports.get(uuid), 2);
            }
            
            currentLines.put("§f§m---------------------", 1);

            for (String entry : board.getEntries()) {
                if (!currentLines.containsKey(entry) && obj.getScore(entry).getScore() > 0) {
                    board.resetScores(entry);
                }
            }

            for (Map.Entry<String, Integer> entry : currentLines.entrySet()) {
                obj.getScore(entry.getKey()).setScore(entry.getValue());
            }
        }
    }

    private boolean isInSpawnRegion(Player p) {
        try {
            ApplicableRegionSet set = WGBukkit.getRegionManager(p.getWorld()).getApplicableRegions(p.getLocation());
            for (ProtectedRegion region : set) {
                if (region.getId().equalsIgnoreCase("spawn")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    class TeamData {
        String name;
        String creatorName;
        UUID creator;
        List<String> members = new ArrayList<>();
        Location hq;
        
        public TeamData(String name, String creatorName, UUID creator) {
            this.name = name;
            this.creatorName = creatorName;
            this.creator = creator;
            this.members.add(creatorName);
        }
    }

    class MarketItem {
        String id;
        String seller;
        UUID sellerUuid;
        ItemStack item;
        double price;

        public MarketItem(String id, String seller, UUID sellerUuid, ItemStack item, double price) {
            this.id = id;
            this.seller = seller;
            this.sellerUuid = sellerUuid;
            this.item = item;
            this.price = price;
        }
    }
}
