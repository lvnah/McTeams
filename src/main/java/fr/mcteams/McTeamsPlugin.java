package fr.mcteams;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class McTeamsPlugin extends JavaPlugin implements CommandExecutor {

    // Bases de données en mémoire (À sauvegarder dans des fichiers config.yml ou JSON pour la prod)
    private Location spawnLocation;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final List<MarketItem> market = new ArrayList<>();

    @Override
    public void onEnable() {
        // Enregistrement des commandes
        String[] cmds = {"setspawn", "go", "team", "deposit", "balance", "sell", "buy", "buyview"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
        }

        // Scoreboard Updater (chaque seconde)
        Bukkit.getScheduler().runTaskTimer(this, this::updateScoreboards, 0L, 20L);
        
        getLogger().info("McTeams est activé !");
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
                p.sendMessage("§aSpawn défini !");
                break;

            case "go":
                if (args.length == 0) {
                    p.sendMessage("§cUsage: /go set <nom> ou /go <nom>");
                    return true;
                }
                Map<String, Location> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());
                
                if (args[0].equalsIgnoreCase("set") && args.length == 2) {
                    if (homes.size() >= 3) {
                        p.sendMessage("§cTu as déjà atteint la limite de 3 /go !");
                        return true;
                    }
                    homes.put(args[1].toLowerCase(), p.getLocation());
                    p.sendMessage("§aGo '" + args[1] + "' défini !");
                } else {
                    String homeName = args[0].toLowerCase();
                    if (homes.containsKey(homeName)) {
                        if (isPlayerNearby(p, 25)) {
                            p.sendMessage("§cTu ne peux pas te téléporter, un joueur est à moins de 25 blocs !");
                            return true;
                        }
                        p.teleport(homes.get(homeName));
                        p.sendMessage("§aTéléportation au go " + homeName + " !");
                    } else {
                        p.sendMessage("§cCe go n'existe pas.");
                    }
                }
                break;

            case "team":
                if (args.length == 0) {
                    p.sendMessage("§cUsage: /team <create|invite|join|leave|kick|sethq|hq|info>");
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
                    p.sendMessage("§aTu as déposé " + goldCount + " lingots d'or. Nouveau solde: " + balances.get(uuid));
                } else {
                    p.sendMessage("§cTu n'as pas d'or dans ton inventaire !");
                }
                break;

            case "balance":
                p.sendMessage("§aTon solde est de : §e" + balances.getOrDefault(uuid, 0.0) + " Or");
                break;

            case "sell":
                // Usage: /sell <quantite> <prix>
                if (args.length != 2) {
                    p.sendMessage("§cUsage: /sell <quantité> <prix>");
                    return true;
                }
                try {
                    int qty = Integer.parseInt(args[0]);
                    double price = Double.parseDouble(args[1]);
                    ItemStack inHand = p.getItemInHand();
                    if (inHand == null || inHand.getType() == Material.AIR || inHand.getAmount() < qty) {
                        p.sendMessage("§cTu n'as pas assez de cet item en main !");
                        return true;
                    }
                    inHand.setAmount(inHand.getAmount() - qty);
                    ItemStack toSell = inHand.clone();
                    toSell.setAmount(qty);
                    
                    market.add(new MarketItem(UUID.randomUUID().toString().substring(0, 5), p.getName(), toSell, price));
                    p.sendMessage("§aItem mis en vente pour " + price + " Or !");
                } catch (NumberFormatException e) {
                    p.sendMessage("§cMontants invalides.");
                }
                break;
                
            case "buyview":
                p.sendMessage("§6--- Marché ---");
                for (MarketItem item : market) {
                    p.sendMessage("§eID: " + item.id + " §f| §b" + item.item.getAmount() + "x " + item.item.getType() + " §f| §aPrix: " + item.price + " Or §f| §7Vendeur: " + item.seller);
                }
                break;

            case "buy":
                // Usage: /buy <id>
                if (args.length != 1) {
                    p.sendMessage("§cUsage: /buy <id>");
                    return true;
                }
                MarketItem toBuy = market.stream().filter(m -> m.id.equalsIgnoreCase(args[0])).findFirst().orElse(null);
                if (toBuy == null) {
                    p.sendMessage("§cCet ID n'existe pas.");
                    return true;
                }
                double balance = balances.getOrDefault(uuid, 0.0);
                if (balance >= toBuy.price) {
                    balances.put(uuid, balance - toBuy.price);
                    p.getInventory().addItem(toBuy.item);
                    market.remove(toBuy);
                    p.sendMessage("§aAchat réussi !");
                } else {
                    p.sendMessage("§cTu n'as pas assez d'or !");
                }
                break;
        }
        return true;
    }

    // --- LOGIQUE DES TEAMS ---
    private void handleTeamCommand(Player p, String[] args) {
        String action = args[0].toLowerCase();
        UUID uuid = p.getUniqueId();
        String currentTeam = playerTeam.get(uuid);

        switch (action) {
            case "create":
                if (currentTeam != null) { p.sendMessage("§cTu es déjà dans une team."); return; }
                if (args.length < 2) { p.sendMessage("§cUsage: /team create <nom>"); return; }
                String teamName = args[1];
                if (teams.containsKey(teamName)) { p.sendMessage("§cNom déjà pris."); return; }
                
                Team newTeam = new Team(teamName, p.getName(), uuid);
                teams.put(teamName, newTeam);
                playerTeam.put(uuid, teamName);
                p.sendMessage("§aTeam " + teamName + " créée !");
                break;

            case "sethq":
                if (currentTeam == null) { p.sendMessage("§cTu n'as pas de team."); return; }
                Team t = teams.get(currentTeam);
                if (!t.creator.equals(uuid)) { p.sendMessage("§cSeul le créateur peut faire ça."); return; }
                t.hq = p.getLocation();
                p.sendMessage("§aHQ de la team défini !");
                break;

            case "hq":
                if (currentTeam == null) { p.sendMessage("§cTu n'as pas de team."); return; }
                if (isPlayerNearby(p, 25)) {
                    p.sendMessage("§cTu ne peux pas te téléporter, un joueur est à moins de 25 blocs !");
                    return;
                }
                Location hq = teams.get(currentTeam).hq;
                if (hq == null) p.sendMessage("§cTa team n'a pas de HQ.");
                else { p.teleport(hq); p.sendMessage("§aTéléportation au HQ !"); }
                break;
                
            case "info":
                String targetTeam = (args.length > 1) ? args[1] : currentTeam;
                if (targetTeam == null || !teams.containsKey(targetTeam)) { p.sendMessage("§cTeam introuvable."); return; }
                Team infoTeam = teams.get(targetTeam);
                p.sendMessage("§6--- Team: " + infoTeam.name + " ---");
                p.sendMessage("§eCréateur: §f" + infoTeam.creatorName);
                p.sendMessage("§eMembres (" + infoTeam.members.size() + "): §f" + String.join(", ", infoTeam.members));
                break;
                
            case "leave":
                if (currentTeam == null) { p.sendMessage("§cTu n'as pas de team."); return; }
                Team lTeam = teams.get(currentTeam);
                if (lTeam.creator.equals(uuid)) {
                    teams.remove(currentTeam);
                    p.sendMessage("§cTu as dissous la team.");
                    // En vrai il faudrait retirer la team de tous les membres
                } else {
                    lTeam.members.remove(p.getName());
                    p.sendMessage("§aTu as quitté la team.");
                }
                playerTeam.remove(uuid);
                break;
                
            // Ajouter /team invite et /team kick selon la même logique
        }
    }

    // --- UTILITAIRES ---
    private boolean isPlayerNearby(Player p, double radius) {
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player && e != p) return true;
        }
        return false;
    }

    // --- SCOREBOARD ---
    private void updateScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("mcteams", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName("§cMcTeams Map");

            List<String> lines = new ArrayList<>();
            lines.add("§f§m------------");
            
            String tName = playerTeam.getOrDefault(p.getUniqueId(), "Aucune");
            if(tName.length() > 10) tName = tName.substring(0, 10) + "..";
            lines.add("§cTeam: §f" + tName);
            
            lines.add("§cBalance: §f" + balances.getOrDefault(p.getUniqueId(), 0.0));
            
            // WorldGuard Check
            boolean inSpawn = isInSpawnRegion(p);
            lines.add("§cSpawn protection: " + (inSpawn ? "§aEnable" : "§cDisable"));
            
            lines.add("§f§m--------------");

            int score = lines.size();
            for (String line : lines) {
                // Astuce pour éviter les doublons dans le scoreboard
                obj.getScore(line + String.join("", Collections.nCopies(score, "§r"))).setScore(score);
                score--;
            }
            p.setScoreboard(board);
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

    // --- CLASSES DE DONNÉES ---
    class Team {
        String name;
        String creatorName;
        UUID creator;
        List<String> members = new ArrayList<>();
        Location hq;
        
        public Team(String name, String creatorName, UUID creator) {
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