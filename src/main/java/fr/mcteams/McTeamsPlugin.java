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

    private Location spawnLocation;
    private final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final List<MarketItem> market = new ArrayList<>();

    @Override
    public void onEnable() {
        String[] cmds = {"setspawn", "go", "team", "deposit", "balance", "sell", "buy", "buyview"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
        }

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
                p.sendMessage("§6Spawn défini §favec succès !");
                break;

            case "go":
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
                
                Team newTeam = new Team(teamName, p.getName(), uuid);
                teams.put(teamName, newTeam);
                playerTeam.put(uuid, teamName);
                p.sendMessage("§6Team §f" + teamName + " §6créée avec succès !");
                break;

            case "sethq":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                Team t = teams.get(currentTeam);
                if (!t.creator.equals(uuid)) { p.sendMessage("§6Seul le créateur §fpeut faire ça."); return; }
                t.hq = p.getLocation();
                p.sendMessage("§6HQ de la team §fdéfini !");
                break;

            case "hq":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
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
                Team infoTeam = teams.get(targetTeam);
                p.sendMessage("§6--- Team: §f" + infoTeam.name + " §6---");
                p.sendMessage("§6Créateur: §f" + infoTeam.creatorName);
                p.sendMessage("§6Membres (" + infoTeam.members.size() + "): §f" + String.join(", ", infoTeam.members));
                break;
                
            case "leave":
                if (currentTeam == null) { p.sendMessage("§6Tu n'as pas §dde team."); return; }
                Team lTeam = teams.get(currentTeam);
                
                if (lTeam.creator.equals(uuid)) {
                    if (lTeam.members.size() > 1) {
                        p.sendMessage("§6Tu ne peux pas dissoudre ta team : §fil reste des membres. Utilise /team kick d'abord.");
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
                Team kTeam = teams.get(currentTeam);
                if (!kTeam.creator.equals(uuid)) {
                    p.sendMessage("§6Seul le créateur §fpeut expulser des membres.");
                    return;
                }
                if (args.length < 2) {
                    p.sendMessage("§6Usage: §f/team kick <joueur>");
                    return;
                }
                String targetName = args[1];
                if (targetName.equalsIgnoreCase(p.getName())) {
                    p.sendMessage("§6Tu ne peux pas §fte kick toi-même.");
                    return;
                }
                if (!kTeam.members.contains(targetName)) {
                    p.sendMessage("§6Ce joueur n'est pas §fdans ta team.");
                    return;
                }

                kTeam.members.remove(targetName);
                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer != null) {
                    playerTeam.remove(targetPlayer.getUniqueId());
                    targetPlayer.sendMessage("§6Tu as été expulsé §fde la team.");
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
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("mcteams", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName("§6SoupTeams Map 1");

            List<String> lines = new ArrayList<>();
            lines.add("§m-----------------------");
            
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