package common;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * RMI config centralizada
 * lê os detalhes de rede de config/system.properties
 */
public class RMIConfig {
    private static Properties props = null;
    private static final String CONFIG_FILE = "config/system.properties";

    // valores default
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "1099";

    /**
     * carregar propriedades do ficheiro de config
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
    // GATEWAY CONFIG(usado pelo Gateway e Barrels/Downloaders)
    // ------------------------------------------------------------------------------

    /**
     * host do gateway (onde o gateway esta a correr)
     */
    public static String getGatewayHost() {
        loadProperties();
        return props.getProperty("gateway.host", DEFAULT_HOST);
    }

    /**
     * porta do gateway
     */
    public static String getGatewayPort() {
        loadProperties();
        return props.getProperty("gateway.port", DEFAULT_PORT);
    }

    /**
     * porta do gateway como inteiro
     */
    public static int getGatewayPortInt() {
        return Integer.parseInt(getGatewayPort());
    }

    /**
     * Gateway RMI URL completo
     */
    public static String getGatewayRMIUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort() + "/Gateway";
    }

    /**
     * BarrelRegistry RMI URL completo
     */
    public static String getBarrelRegistryRMIUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort() + "/BarrelRegistry";
    }

    /**
     * RMI URL base
     */
    public static String getRMIBaseUrl() {
        return "rmi://" + getGatewayHost() + ":" + getGatewayPort();
    }

    // ------------------------------------------------------------------------------
    // CLIENT CONFIG
    // ------------------------------------------------------------------------------

    /**
     * host do gateway do cliente
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
     * porta da gateway do cliente
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
     * porta da gateway do cliente como int
     */
    public static int getClientGatewayPortInt() {
        return Integer.parseInt(getClientGatewayPort());
    }

    /**
     * url completo da gateway para clientes
     */
    public static String getClientGatewayRMIUrl() {
        return "rmi://" + getClientGatewayHost() + ":" + getClientGatewayPort() + "/Gateway";
    }

    /**
     * print da config para debug
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
     * verificar se o ficheiro de configuração está disponível e legível
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
