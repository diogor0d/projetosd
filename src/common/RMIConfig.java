package common;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Centralized RMI configuration utility
 * Reads network configuration from config/system.properties
 */
public class RMIConfig {
    private static Properties props = null;
    private static final String CONFIG_FILE = "config/system.properties";

    // Default values
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "1099";

    /**
     * Load properties from configuration file
     */
    private static void loadProperties() {
        if (props == null) {
            props = new Properties();
            try {
                FileInputStream fis = new FileInputStream(CONFIG_FILE);
                props.load(fis);
                fis.close();
            } catch (Exception e) {
                System.err.println("[RMIConfig] Warning: Could not load " + CONFIG_FILE + ". Using defaults.");
                System.err.println("[RMIConfig] Error: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------------
    // GATEWAY CONFIGURATION (used by Gateway itself and by Barrels/Downloaders)
    // ------------------------------------------------------------------------------

    /**
     * Get Gateway host (where Gateway RMI registry is running)
     */
    public static String getGatewayHost() {
        loadProperties();
        return props.getProperty("gateway.host", DEFAULT_HOST);
    }

    /**
     * Get Gateway port
     */
    public static String getGatewayPort() {
        loadProperties();
        return props.getProperty("gateway.port", DEFAULT_PORT);
    }

    /**
     * Get Gateway port as integer
     */
    public static int getGatewayPortInt() {
        return Integer.parseInt(getGatewayPort());
    }

    /**
     * Get full Gateway RMI URL (for Gateway service)
     */
    public static String getGatewayRMIUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort() + "/Gateway";
    }

    /**
     * Get full BarrelRegistry RMI URL
     */
    public static String getBarrelRegistryRMIUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort() + "/BarrelRegistry";
    }

    /**
     * Get RMI base URL (for binding services)
     */
    public static String getRMIBaseUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort();
    }

    // ------------------------------------------------------------------------------
    // CLIENT CONFIGURATION (allows client to connect to different gateway)
    // ------------------------------------------------------------------------------

    /**
     * Get Client's gateway host (may be different from main gateway)
     * Falls back to gateway.host if not specified
     */
    public static String getClientGatewayHost() {
        loadProperties();
        String clientHost = props.getProperty("client.gateway.host");
        if (clientHost != null && !clientHost.trim().isEmpty()) {
            return clientHost;
        }
        // Fallback to main gateway
        return getGatewayHost();
    }

    /**
     * Get Client's gateway port
     * Falls back to gateway.port if not specified
     */
    public static String getClientGatewayPort() {
        loadProperties();
        String clientPort = props.getProperty("client.gateway.port");
        if (clientPort != null && !clientPort.trim().isEmpty()) {
            return clientPort;
        }
        // Fallback to main gateway
        return getGatewayPort();
    }

    /**
     * Get Client's gateway port as integer
     */
    public static int getClientGatewayPortInt() {
        return Integer.parseInt(getClientGatewayPort());
    }

    /**
     * Get full Gateway RMI URL for Client
     */
    public static String getClientGatewayRMIUrl() {
        return "rmi://" + getClientGatewayHost() + ":" + getClientGatewayPort() + "/Gateway";
    }

    // ------------------------------------------------------------------------------
    // UTILITY METHODS
    // ------------------------------------------------------------------------------

    /**
     * Print current configuration (for debugging)
     */
    public static void printConfig(String componentName) {
        System.out.println("[" + componentName + "] RMI Configuration:");
        System.out.println("  Gateway Host: " + getGatewayHost());
        System.out.println("  Gateway Port: " + getGatewayPort());

        if (componentName.toLowerCase().contains("client")) {
            System.out.println("  Client Gateway Host: " + getClientGatewayHost());
            System.out.println("  Client Gateway Port: " + getClientGatewayPort());
            System.out.println("  Client Gateway URL: " + getClientGatewayRMIUrl());
        } else {
            System.out.println("  Gateway URL: " + getGatewayRMIUrl());
            System.out.println("  BarrelRegistry URL: " + getBarrelRegistryRMIUrl());
        }
    }

    /**
     * Check if configuration file exists and is readable
     */
    public static boolean isConfigAvailable() {
        try {
            FileInputStream fis = new FileInputStream(CONFIG_FILE);
            fis.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
