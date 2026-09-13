package fr.mcteams;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private final Map<UUID, Long> combatTag = new HashMap<>();
    private final List<MarketItem> market = new ArrayList<>();

    @Override
    public void onEnable() {
        String[] cmds = {"setspawn", "spawn", "go", "team", "deposit", "balance", "sell", "buy", "buyview", "setrank"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 0L, 20L);
        getLogger().info("McTeams est activé avec les systèmes de CombatTag et Spawn !");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        playerRanks.putIfAbsent(p.getUniqueId(), "default");
        setupPlayerScoreboard(p);
        if (spawnLocation != null) {
            p.teleport(spawnLocation);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isInCombat(p)) {
            p.setHealth(0.0); // Tue le joueur si déconnexion en combat
            Bukkit.broadcastMessage("§6[Combat] §f" + p.getName() + " §6s'est déconnecté en combat et a été tué !");
        }
        combatTag.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        combatTag.remove(p.getUniqueId());
        
        // Téléportation immédiate au spawn après la mort
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (spawnLocation != null) {
                p.spigot().respawn();
                p.teleport(spawnLocation);
                p.sendMessage("§6Tu es mort et as été réinitialisé au §fspawn §6avec protection !");
            }
        }, 2L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            Player victim = (Player) e.getEntity();
            Player attacker = (Player) e.getDamager();

            // Empêcher les dégâts si l'un d'eux est protégé au spawn
            if (isInSpawnRegion(victim) || isInSpawnRegion(attacker)) {
                e.setCancelled(true);
                attacker.sendMessage("§6Impossible de frapper : cible ou attaquant sous protection du §fspawn §6!");
                return;
            }

            // Application du Combat Tag (45 secondes)
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
        String prefix = getRankPrefix(rank);
        e.setFormat(prefix + " §f" + p.getName() + " §7> §f" + e.getMessage());
    }

    private String getRankPrefix(String rank) {
        switch (rank.toLowerCase()) {
            case "vip": return "§6[VIP]";
            case "mod": case "moderator": return "§b[Mod]";
            case "admin": return "§c[Admin]";
            default: return "§f[Player]";
        }
    }

    private void setupPlayerScoreboard(Player target) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                p.setScoreboard(board);
            }
            createRankTeamInBoard(board, "01admin", "§c[Admin] ");
            createRankTeamInBoard(board, "02mod", "§b[Mod] ");
            createRankTeamInBoard(board, "03vip", "§6[VIP] ");
            createRankTeamInBoard(board, "04default", "§f[Player] ");
            assignPlayerToRankTeam(board, target);
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
        String teamKey = "04default";
        if (rank.equals("admin")) teamKey = "01admin";
        else if (rank.equals("mod") || rank.equals("moderator")) teamKey = "02mod";
        else if (rank.equals("vip")) teamKey = "03vip";

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

        switch (command.getName().toLowerCase()) {
            case "setspawn":
                if (!p.isOp()) return true;
                spawnLocation = p.getLocation();
                p.sendMessage("§6Spawn défini §favec succès !");
                break;

            case "spawn":
                if (spawnLocation == null) {
                    p.sendMessage("§6Le spawn n'a pas §fdéfini par un admin.");
                    return true;
                }
                if (isInCombat(p)) {
                    p.sendMessage("§6Impossible de faire §f/spawn §6en étant en combat tag !");
                    return true;
                }
                p.sendMessage("§6Téléportation au spawn §fdans 15 secondes... Ne bouge pas !");
                Location locBefore = p.getLocation();
                
                // Task de 15 secondes pour /spawn
                new BukkitRunnable() {
                    int countdown = 15;
                    @Override
                    public void run() {
                        if (!p.isOnline() || !p.getLocation().getWorld().equals(locBefore.getWorld()) || p.getLocation().distanceSquared(locBefore) > 0.5) {
                            p.sendMessage("§6Téléportation annulée §f(mouvement détecté).");
                            cancel();
                            return;
                        }
                        if (isInCombat(p)) {
                            p.sendMessage("§6Téléportation annulée §f(attaqué en combat).");
                            cancel();
                            return;
                        }
                        if (countdown <= 0) {
                            p.teleport(spawnLocation);
                            p.sendMessage("§6Téléportation effectuée §fau spawn !");
                            cancel();
                            return;
                        }
                        if (countdown <= 5 || countdown == 15) {
                            p.sendMessage("§6Téléportation dans §f" + countdown + " secondes...");
                        }
                        countdown--;
                    }
                }.runTaskTimer(this, 0L, 20L);
                break;

            case "setrank":
                if (!p.isOp()) {
                    p.sendMessage("§6Tu n'as pas la permission §fde faire ça.");
                    return true;
                }
                if (args.length != 2) {
                    p.sendMessage("§6Usage: §f/setrank <joueur> <default|vip|mod|admin>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage("§6Joueur introuvable §fou hors ligne.");
                    return true;
                }
                String newRank = args[1].toLowerCase();
                if (!Arrays.asList("default", "vip", "mod", "admin").contains(newRank)) {
                    p.sendMessage("§6Grades valides : §fdefault, vip, mod, admin");
                    return true;
                }
                playerRanks.put(target.getUniqueId(), newRank);
                setupPlayerScoreboard(target);
                p.sendMessage("§6Grade de §f" + target.getName() + " §6défini sur : §f" + newRank);
                target.sendMessage("§6Ton grade a été mis à jour : §f" + newRank);
                break;

            case "go":
                if (isInCombat(p)) {
                    p.sendMessage("§6Impossible de se téléporter §fen combat tag !");
                    return true;
                }
                if (args.length == 0) {
                    p.sendMessage("§6Usage: §f/go set <nom> ou /go <nom>");
                    return true;
                }
                Map<String, Location> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());
                if (args[0].equalsIgnoreCase("set") && args.length == 2) {
                    if (homes.size() >= 3) {
                        p.sendMessage("§6Tu as déjà atteint la limite de §f3 /go §6!");
                        return true;
                    }
                    homes.put(args[1].toLowerCase(), p.getLocation());
                    p.sendMessage("§6Go §f'" + args[1] + "' §6défini !");
                } else {
                    String homeName = args[0].toLowerCase();
                    if (homes.containsKey(homeName)) {
                        if (isPlayerNearby(p, 25)) {
                            p.sendMessage("§6Téléportation bloquée : §fun joueur est à moins de 25 blocs !");
                            return true;
                        }
                        p.teleport(homes.get(homeName));
                        p.sendMessage("§6Téléportation au go §f" + homeName + " §6!");
                    } else {
                        p.sendMessage("§6Ce go §fn'existe pas.");
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
                    p.sendMessage("§6Tu as déposé §f" + goldCount + " lingots d'or. §6Nouveau solde: §f" + balances.get(uuid));
                } else {
                    p.sendMessage("§6Tu n'as pas d'or §fdans ton inventaire !");
                }
                break;

            case "balance":
                p.sendMessage("§6Ton solde est de : §f" + balances.getOrDefault(uuid, 0.0) + " Or");
                break;

            case "sell":
                if (args.length != 2) {
                    p.sendMessage("§6Usage: §f/sell <quantité> <prix>");
                    return true;
                }
                try {
                    int qty = Integer.parseInt(args[0]);
                    double price = Double.parseDouble(args[1]);
                    ItemStack inHand = p.getItemInHand();
                    if (inHand == null || inHand.getType() == Material.AIR || inHand.getAmount() < qty) {
                        p.sendMessage("§6Tu n'as pas assez §fde cet item en main !");
                        return true;
                    }
                    inHand.setAmount(inHand.getAmount() - qty);
                    ItemStack toSell = inHand.clone();
                    toSell.setAmount(qty);
                    
                    market.add(new MarketItem(UUID.randomUUID().toString().substring(0, 5), p.getName(), toSell, price));
                    p.sendMessage("§6Item mis en vente pour §f" + price + " Or §6!");
                } catch (NumberFormatException e) {
                    p.sendMessage("§6Montants §finvalides.");
                }
                break;
                
            case "buyview":
                p.sendMessage("§6--- Marché ---");
                for (MarketItem item : market) {
                    p.sendMessage("§6ID: §f" + item.id + " §6| §f" + item.item.getAmount() + "x " + item.item.getType() + " §6| Prix: §f" + item.price + " Or §6| Vendeur: §f" + item.seller);
                }
                break;

            case "buy":
                if (args.length != 1) {
                    p.sendMessage("§6Usage: §f/buy <id>");
                    return true;
                }
                MarketItem toBuy = market.stream().filter(m -> m.id.equalsIgnoreCase(args[0])).findFirst().orElse(null);
                if (toBuy == null) {
                    p.sendMessage("§6Cet ID §fn'existe pas.");
                    return true;
                }
                double balance = balances.getOrDefault(uuid, 0.0);
                if (balance >= toBuy.price) {
                    balances.put(uuid, balance - toBuy.price);
                    p.getInventory().addItem(toBuy.item);
                    market.remove(toBuy);
                    p.sendMessage("§6Achat §fréussi !");
                } else {
                    p.sendMessage("§6Tu n'as pas assez §fd'or !");
                }
                break;
        }
        return true;
    }

    private void handleTeamCommand(Player p, String[] args) {
        String action = args[0].toLowerCase();
        UUID uuid = p.getUniqueId();
        String currentTeam = playerTeam.get(uuid);

        switch (action) {
            case "create":
                if (currentTeam != null) { p.sendMessage("§6Tu es déjà §fdans une team."); return; }
                if (args.length < 2) { p.sendMessage("§6Usage: §f/team create <nom>"); return; }
                String teamName = args[1];
                if (teams.containsKey(teamName)) { p.sendMessage("§6Ce nom est déjà §fpris."); return; }
                
                TeamData newTeam = new TeamData(teamName, p.getName(), uuid);
                teams.put(teamName, newTeam);
                playerTeam.put(uuid, teamName);
                p.sendMessage("§6Team §f" + teamName + " §6créée avec succès !");
                break;

            case "sethq":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                TeamData t = teams.get(currentTeam);
                if (!t.creator.equals(uuid)) { p.sendMessage("§6Seul le créateur §fpeut faire ça."); return; }
                t.hq = p.getLocation();
                p.sendMessage("§6HQ de la team §fdéfini !");
                break;

            case "hq":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                if (isInCombat(p)) {
                    p.sendMessage("§6Impossible de se téléporter au HQ §fen combat tag !");
                    return;
                }
                if (isPlayerNearby(p, 25)) {
                    p.sendMessage("§6Téléportation bloquée : §fune entité/joueur est à moins de 25 blocs !");
                    return;
                }
                Location hq = teams.get(currentTeam).hq;
                if (hq == null) p.sendMessage("§6Ta team n'a pas §dde HQ.");
                else { p.teleport(hq); p.sendMessage("§6Téléportation au §fHQ §6!"); }
                break;
                
            case "info":
                String targetTeam = (args.length > 1) ? args[1] : currentTeam;
                if (targetTeam == null || !teams.containsKey(targetTeam)) { p.sendMessage("§6Team §fintrouvable."); return; }
                TeamData infoTeam = teams.get(targetTeam);
                p.sendMessage("§6--- Team: §f" + infoTeam.name + " §6---");
                p.sendMessage("§6Créateur: §f" + infoTeam.creatorName);
                p.sendMessage("§6Membres (" + infoTeam.members.size() + "): §f" + String.join(", ", infoTeam.members));
                break;
                
            case "leave":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                TeamData lTeam = teams.get(currentTeam);
                if (lTeam.creator.equals(uuid)) {
                    if (lTeam.members.size() > 1) {
                        p.sendMessage("§6Tu ne peux pas dissoudre ta team : §fil reste des membres.");
                        return;
                    }
                    teams.remove(currentTeam);
                    playerTeam.remove(uuid);
                    p.sendMessage("§6Tu as dissous §fta team.");
                } else {
                    lTeam.members.remove(p.getName());
                    playerTeam.remove(uuid);
                    p.sendMessage("§6Tu as quitté §fta team.");
                }
                break;

            case "kick":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                TeamData kTeam = teams.get(currentTeam);
                if (!kTeam.creator.equals(uuid)) {
                    p.sendMessage("§6Seul le créateur §fpeut expulser des membres.");
                    return;
                }
                if (args.length < 2) {
                    p.sendMessage("§6Usage: §f/team kick <joueur>");
                    return;
                }
                String targetName = args[1];
                if (!kTeam.members.contains(targetName)) {
                    p.sendMessage("§6Ce joueur n'est pas §fdans ta team.");
                    return;
                }
                kTeam.members.remove(targetName);
                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer != null) {
                    playerTeam.remove(targetPlayer.getUniqueId());
                    targetPlayer.sendMessage("§6Tu été expulsé §fde la team.");
                }
                p.sendMessage("§6Le joueur §f" + targetName + " §6a été expulsé.");
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
            Scoreboard board = p.getScoreboard();
            Objective obj = board.getObjective("mcteams");
            if (obj == null) {
                obj = board.registerNewObjective("mcteams", "dummy");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            obj.setDisplayName("§6SoupTeams §f[Map 1]");

            List<String> lines = new ArrayList<>();
            lines.add("§m---------------------");
            
            String tName = playerTeam.getOrDefault(p.getUniqueId(), "Aucune");
            if(tName.length() > 10) tName = tName.substring(0, 10) + "..";
            lines.add("§6Team: §f" + tName);
            
            lines.add("§6Balance: §f" + balances.getOrDefault(p.getUniqueId(), 0.0));
            
            boolean inSpawn = isInSpawnRegion(p);
            lines.add("§6Spawn protection: " + (inSpawn ? "§aEnable" : "§cDisable"));
            
            lines.add("§f§m---------------------");

            int score = lines.size();
            for (String line : lines) {
                obj.getScore(line + String.join("", Collections.nCopies(score, "§r"))).setScore(score);
                score--;
            }
        }
    }

    private boolean isInSpawnRegion(Player p) {
        ApplicableRegionSet set = WGBukkit.getRegionManager(p.getWorld()).getApplicableRegions(p.getLocation());
        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase("spawn")) {
                return true;
            }
        }
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
        ItemStack item;
        double price;

        public MarketItem(String id, String seller, ItemStack item, double price) {
            this.id = id;
            this.seller = seller;
            this.item = item;
            this.price = price;
        }
    }
}