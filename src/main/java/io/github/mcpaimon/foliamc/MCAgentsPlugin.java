package io.github.mcpaimon.foliamc;

import io.github.mcengine.mcextension.api.HostContext;
import io.github.mcengine.mcextension.common.MCExtensionManager;
import io.github.mcengine.mcextension.foliamc.commands.MCExtensionCommand;
import io.github.mcengine.mcextension.foliamc.context.FoliaHostContext;
import io.github.mcengine.mcextension.foliamc.tabcompleters.MCExtensionTabCompleter;
import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.common.MCAgentsProvider;
import io.github.mcpaimon.common.database.api.MCAgentsAPI;
import io.github.mcpaimon.common.database.postgresql.MCAgentsPostgreSQL;
import io.github.mcpaimon.common.database.sqlite.MCAgentsSQLite;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Main plugin class for MCAgents on FoliaMC.
 */
public class MCAgentsPlugin extends JavaPlugin {

    /**
     * Provider serving as the central API and entry point for AI workflows and queries.
     */
    private MCAgentsProvider provider;

    /**
     * Manager handling the dynamic loading and unloading of extensions.
     */
    private MCExtensionManager extensionManager;

    /**
     * Host context for MCExtension platform abstraction.
     */
    private HostContext hostContext;

    /**
     * Set of player UUIDs currently in an active AI chat session.
     */
    private final Set<UUID> aiChatSessions = new HashSet<>();

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        
        saveDefaultConfig();
        if (!getConfig().getBoolean("enable", false)) {
            logger.warning("Plugin is set to 'enable: false' in config.yml. Shutting down MCAgents...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        IAIDatabase database;
        String dbType = getConfig().getString("database.type", "sqlite");
        
        if (dbType.equalsIgnoreCase("postgresql")) {
            logger.info("Using PostgreSQL Database...");
            database = new MCAgentsPostgreSQL(
                getConfig().getString("database.postgresql.host"),
                getConfig().getInt("database.postgresql.port"),
                getConfig().getString("database.postgresql.database"),
                getConfig().getString("database.postgresql.username"),
                getConfig().getString("database.postgresql.password")
            );
        } else if (dbType.equalsIgnoreCase("api")) {
            logger.info("Using Remote API Database...");
            database = new MCAgentsAPI(
                getConfig().getString("database.api.url"),
                getConfig().getString("database.api.token")
            );
        } else {
            logger.info("Using SQLite Database...");
            database = new MCAgentsSQLite(new File(getDataFolder(), getConfig().getString("database.sqlite.file", "mcagents.db")));
        }

        String secretKey = getConfig().getString("token.secret", "secretkey");
        int maxWorkflowIterations = getConfig().getInt("max_workflow_iterations", 5);
        
        this.provider = new MCAgentsProvider(database, secretKey, maxWorkflowIterations);

        // Auto-create platforms and models from config safely
        List<String> platformsConfig = getConfig().getStringList("platforms");
        if (platformsConfig != null && !platformsConfig.isEmpty()) {
            try {
                // Fetch existing platforms to avoid duplicates
                List<AIPlatform> existingPlatforms = this.provider.getPlatforms().join();
                
                for (String entry : platformsConfig) {
                    // Split by comma and support multiple models trailing behind the URL
                    String[] parts = entry.split(",");
                    if (parts.length >= 3) {
                        String pName = parts[0].trim();
                        String pUrl = parts[1].trim();
                        
                        // Check if platform already exists
                        AIPlatform targetPlatform = existingPlatforms.stream()
                                .filter(p -> p.displayName().equalsIgnoreCase(pName))
                                .findFirst()
                                .orElse(null);
                                
                        if (targetPlatform == null) {
                            // Create new platform and block until finished (.join)
                            targetPlatform = this.provider.registerPlatform(pName, pUrl).join();
                            existingPlatforms.add(targetPlatform); // Update local cache
                            logger.info("Auto-registered new platform: " + pName);
                        } else {
                            logger.info("Found existing platform: " + pName);
                        }
                        
                        // Register ALL models listed for this platform (Loop from index 2 onwards)
                        for (int i = 2; i < parts.length; i++) {
                            String pModel = parts[i].trim();
                            if (!pModel.isEmpty()) {
                                this.provider.registerModel(targetPlatform.id(), pModel).join();
                                logger.info("Auto-registered model: " + pModel + " for platform: " + pName);
                            }
                        }
                    } else {
                        logger.warning("Invalid format in config.yml -> platforms. Expected 'name,url,model1,model2...', but got: " + entry);
                    }
                }
            } catch (Exception e) {
                logger.severe("Error during auto-creating platforms/models: " + e.getMessage());
                e.printStackTrace(); // Print full stack trace for debugging
            }
        } else {
            logger.warning("No platforms defined in config.yml or 'platforms' list is empty.");
        }

        // Initialize Console Account
        if (getConfig().contains("console.platform")) {
            String cPlatform = getConfig().getString("console.platform");
            String cModel = getConfig().getString("console.model");
            String cToken = getConfig().getString("console.token");
            
            this.provider.getPlatforms().thenAccept(platforms -> {
                platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(cPlatform)).findFirst().ifPresent(p -> {
                    this.provider.setupAccount("console", "00000000-0000-0000-0000-000000000000", p.id(), cToken).join();
                    this.provider.setActiveSession("console", "00000000-0000-0000-0000-000000000000", p.id(), cModel).join();
                    logger.info("Console AI Account successfully configured.");
                });
            });
        }

        logger.info("Loading extensions...");
        
        // Initialize MCExtension API via FoliaHostContext
        this.hostContext = new FoliaHostContext(this);
        this.extensionManager = new MCExtensionManager(-1, "foliamc");
        
        // Use an asynchronous executor for Folia (Global Async Scheduler)
        Executor extensionExecutor = command -> Bukkit.getAsyncScheduler().runNow(this, task -> command.run());
        this.extensionManager.loadAllExtensions(this.hostContext, extensionExecutor);

        // Register MCExtension Commands & TabCompleter
        PluginCommand extensionCmd = getCommand("mcaiextension");
        if (extensionCmd != null) {
            extensionCmd.setExecutor(new MCExtensionCommand(this.hostContext, this.extensionManager, extensionExecutor));
            extensionCmd.setTabCompleter(new MCExtensionTabCompleter(this.extensionManager));
        } else {
            logger.warning("Command 'mcaiextension' is not defined in plugin.yml. Skipped command registration.");
        }

        logger.info("MCAgents Plugin has been successfully enabled on FoliaMC!");
    }

    @Override
    public void onDisable() {
        if (this.extensionManager != null && this.hostContext != null) {
            Executor disableExecutor = command -> {
                // If the plugin is still enabled, run the disable task asynchronously via Folia Async Scheduler
                if (this.isEnabled()) {
                    Bukkit.getAsyncScheduler().runNow(this, task -> command.run());
                } else {
                    // Fallback to synchronous execution if the server is shutting down
                    command.run(); 
                }
            };
            this.extensionManager.disableAllExtensions(this.hostContext, disableExecutor);
        }
        if (this.provider != null) this.provider.shutdown().join();
        getLogger().info("MCAgents Plugin has been disabled on FoliaMC!");
    }
    
    /**
     * Gets the MCAgentsProvider instance which serves as the central API.
     * @return The active MCAgentsProvider.
     */
    public MCAgentsProvider getProvider() { return this.provider; }
    
    /**
     * Gets the set of UUIDs of players currently in AI chat sessions.
     * @return A set of player UUIDs.
     */
    public Set<UUID> getAiChatSessions() { return this.aiChatSessions; }
}
