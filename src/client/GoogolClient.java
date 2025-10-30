package client;

import common.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Scanner;

/**
 * ANSI color codes for terminal colors
 * Auto-disables when colors are not supported (e.g., Maven output)
 */
class Colors {
    // Detect if colors are supported
    private static final boolean COLORS_ENABLED = System.console() != null && System.getenv().get("TERM") != null;

    // Helper method to return color code or empty string
    private static String color(String code) {
        return COLORS_ENABLED ? code : "";
    }

    // Reset
    public static final String RESET = color("\033[0m");

    // Regular colors
    public static final String BLACK = color("\033[0;30m");
    public static final String RED = color("\033[0;31m");
    public static final String GREEN = color("\033[0;32m");
    public static final String YELLOW = color("\033[0;33m");
    public static final String BLUE = color("\033[0;34m");
    public static final String PURPLE = color("\033[0;35m");
    public static final String CYAN = color("\033[0;36m");
    public static final String WHITE = color("\033[0;37m");

    // Bold
    public static final String BOLD = color("\033[1m");
    public static final String BOLD_RED = color("\033[1;31m");
    public static final String BOLD_GREEN = color("\033[1;32m");
    public static final String BOLD_YELLOW = color("\033[1;33m");
    public static final String BOLD_BLUE = color("\033[1;34m");
    public static final String BOLD_PURPLE = color("\033[1;35m");
    public static final String BOLD_CYAN = color("\033[1;36m");
    public static final String BOLD_WHITE = color("\033[1;97m");

    // Background
    public static final String BG_BLUE = color("\033[44m");
    public static final String BG_GREEN = color("\033[42m");
    public static final String BG_RED = color("\033[41m");
}

/**
 * Cliente RMI para interagir com o sistema Googol
 */
public class GoogolClient {

    private volatile IGateway gateway; // volatile for thread-safe reconnection
    private Scanner scanner;
    private volatile boolean connected;
    private volatile boolean running; // Track if client should keep running

    public GoogolClient() {
        scanner = new Scanner(System.in);
        connected = false;
        running = true;

        // Add shutdown hook for clean exit on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Flush output streams to avoid Maven/Jansi issues
                System.out.flush();
                System.err.flush();

                running = false;
                if (scanner != null) {
                    scanner.close();
                }
                // Clear screen and show clean goodbye message
                System.out.print("\033[H\033[2J");
                System.out.flush();
                System.out.println(Colors.BOLD_CYAN + "\n👋 Thank you for using Googol!" + Colors.RESET);
                System.out.println(Colors.CYAN + "Goodbye!\n" + Colors.RESET);
                System.out.flush();
            } catch (Exception e) {
                // Suppress output errors during shutdown (Maven/Jansi issues)
            }
        }));
    }

    /**
     * Clears the screen (works in most terminals)
     */
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /**
     * Prints the Googol banner
     */
    private void printBanner() {
        System.out.println(Colors.BOLD_CYAN);
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║                                                           ║");
        System.out.println("║   " + Colors.BOLD_WHITE + "  ██████╗  ██████╗  ██████╗  ██████╗  ██████╗ ██╗     "
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║   " + Colors.BOLD_WHITE + " ██╔════╝ ██╔═══██╗██╔═══██╗██╔════╝ ██╔═══██╗██║     "
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║   " + Colors.BOLD_YELLOW + " ██║  ███╗██║   ██║██║   ██║██║  ███╗██║   ██║██║     "
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║   " + Colors.BOLD_YELLOW + " ██║   ██║██║   ██║██║   ██║██║   ██║██║   ██║██║     "
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║   " + Colors.BOLD_GREEN + " ╚██████╔╝╚██████╔╝╚██████╔╝╚██████╔╝╚██████╔╝███████╗"
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║   " + Colors.BOLD_GREEN + "  ╚═════╝  ╚═════╝  ╚═════╝  ╚═════╝  ╚═════╝ ╚══════╝"
                + Colors.BOLD_CYAN + "   ║");
        System.out.println("║                                                           ║");
        System.out.println("║          " + Colors.BOLD_WHITE + "Distributed Search Engine" + Colors.BOLD_CYAN
                + "                     ║");
        System.out.println("║                                                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println(Colors.RESET);
        // sleep for 1.5s
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Attempts to connect to gateway indefinitely with retry logic
     */
    private void connectToGateway() {
        clearScreen();
        printBanner();

        System.out.println(Colors.YELLOW + "🔌 Connecting to Gateway..." + Colors.RESET);
        System.out.println();

        int attempts = 0;
        while (!connected) {
            try {
                attempts++;
                System.out.print(Colors.CYAN + "   Attempt " + attempts + "... ");

                IGateway tempGateway = (IGateway) Naming.lookup("rmi://localhost/Gateway");
                // Verify connection is alive
                tempGateway.getSystemStats();

                gateway = tempGateway; // Update reference
                connected = true;

                System.out.println(Colors.BOLD_GREEN + "✓ Connected!" + Colors.RESET);
                clearScreen();
                printBanner();
                System.out.println(Colors.GREEN + "✓ Successfully connected to Googol Gateway" + Colors.RESET);
                System.out.println();

            } catch (Exception e) {
                System.out.println(Colors.RED + "✗ Failed" + Colors.RESET);
                System.out.print(Colors.YELLOW + "   Retrying in 3 seconds" + Colors.RESET);
                try {
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(1000);
                        System.out.print(".");
                    }
                    System.out.println();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Executes a gateway operation with automatic reconnection on failure
     */
    private <T> T executeWithReconnect(GatewayOperation<T> operation) throws Exception {
        while (true) {
            try {
                if (!connected) {
                    connectToGateway();
                }
                return operation.execute(gateway);

            } catch (RemoteException e) {
                // Check if this is a validation/business logic error (not a connection issue)
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Invalid URL") || errorMsg.contains("already indexed"))) {
                    // This is a validation/business error, not a connection problem
                    // Throw it to the caller to handle
                    throw e;
                }

                // Otherwise, it's a connection problem - try to reconnect
                System.out.println();
                System.out.println(Colors.BOLD_RED + "⚠ Connection Lost!" + Colors.RESET);
                connected = false;
                gateway = null; // Clear old reference
                System.out.println(Colors.YELLOW + "🔄 Reconnecting..." + Colors.RESET);
                connectToGateway();
                // Retry operation after reconnection
            }
        }
    }

    /**
     * Functional interface for gateway operations
     */
    @FunctionalInterface
    private interface GatewayOperation<T> {
        T execute(IGateway gateway) throws Exception;
    }

    public void run() {
        // Initial connection
        connectToGateway();

        while (running) {
            try {
                printMenu();

                // Check if scanner has input available (prevents exception on Ctrl+C)
                if (!scanner.hasNextLine()) {
                    break;
                }

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        indexURL();
                        break;
                    case "2":
                        search();
                        break;
                    case "3":
                        getPagesPointingTo();
                        break;
                    case "4":
                        showStatistics();
                        break;
                    case "5":
                        printURLQueue();
                        break;
                    case "0":
                        running = false;
                        clearScreen();
                        System.out.println(Colors.BOLD_CYAN + "\n👋 Thank you for using Googol!" + Colors.RESET);
                        System.out.println(Colors.CYAN + "Goodbye!\n" + Colors.RESET);
                        break;
                    default:
                        System.out.println(Colors.RED + "❌ Invalid option! Please try again." + Colors.RESET);
                }

                if (running && !choice.equals("2") && !choice.equals("0")) {
                    System.out.println();
                    System.out.print(Colors.YELLOW + "Press Enter to continue..." + Colors.RESET);
                    try {
                        if (scanner.hasNextLine()) {
                            scanner.nextLine();
                        }
                    } catch (Exception e) {
                        // Scanner closed - exit gracefully
                        running = false;
                        break;
                    }
                }

            } catch (java.util.NoSuchElementException | IllegalStateException e) {
                // Scanner closed (Ctrl+C pressed) - exit gracefully
                running = false;
                break;
            } catch (Exception e) {
                if (running) {
                    System.out.println(Colors.RED + "❌ Error: " + e.getMessage() + Colors.RESET);
                    System.out.println();
                }
            }
        }

        // Clean close - shutdown hook will handle display
        if (scanner != null && !Thread.currentThread().isInterrupted()) {
            try {
                scanner.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void printMenu() {
        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "╔════════════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "         GOOGOL SEARCH ENGINE           "
                + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╠════════════════════════════════════════════╣" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║                                            ║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.GREEN + "1." + Colors.WHITE
                + " 🔗 Index new URL                  " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.GREEN + "2." + Colors.WHITE
                + " 🔍 Search                         " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.GREEN + "3." + Colors.WHITE
                + " 🔗 Get pages pointing to URL      " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.GREEN + "4." + Colors.WHITE
                + " 📊 View statistics                " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.GREEN + "5." + Colors.WHITE
                + " 📋 View URL queue                 " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║  " + Colors.RED + "0." + Colors.WHITE
                + " 🚪 Exit                           " + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║                                            ║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚════════════════════════════════════════════╝" + Colors.RESET);
        System.out.print(Colors.YELLOW + "\n➤ Choose an option: " + Colors.RESET);
    }

    private void indexURL() throws Exception {
        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "        INDEX NEW URL              "
                + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
        System.out.println();
        System.out.print(Colors.CYAN + "🔗 Enter URL: " + Colors.RESET);
        String url = scanner.nextLine().trim();

        if (url.isEmpty()) {
            System.out.println(Colors.RED + "❌ URL cannot be empty!" + Colors.RESET);
            return;
        }

        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        final String finalUrl = url;

        System.out.println(Colors.YELLOW + "\n⏳ Submitting URL for indexing..." + Colors.RESET);

        try {
            executeWithReconnect(gw -> {
                gw.indexURL(finalUrl);
                return null;
            });

            System.out.println(Colors.BOLD_GREEN + "✓ URL submitted successfully!" + Colors.RESET);
            System.out.println(Colors.WHITE + "  URL: " + Colors.CYAN + finalUrl + Colors.RESET);
        } catch (RemoteException e) {
            // Handle validation errors from Gateway
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Invalid URL")) {
                System.out.println(Colors.BOLD_RED + "\n❌ Invalid URL!" + Colors.RESET);
                System.out.println(Colors.RED + "   " + errorMsg + Colors.RESET);
            } else if (errorMsg != null && errorMsg.contains("already indexed")) {
                System.out.println(Colors.BOLD_YELLOW + "\n⚠ URL Already Indexed!" + Colors.RESET);
                System.out.println(
                        Colors.YELLOW + "   This URL has already been processed by the system." + Colors.RESET);
            } else {
                throw e; // Re-throw other exceptions
            }
        }
    }

    private void search() throws Exception {
        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "           SEARCH              "
                + Colors.BOLD_CYAN + "    ║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
        System.out.println();
        System.out.print(Colors.CYAN + "🔍 Enter search terms: " + Colors.RESET);
        String query = scanner.nextLine().trim();

        if (query.isEmpty()) {
            System.out.println(Colors.RED + "❌ Search query cannot be empty!" + Colors.RESET);
            return;
        }

        int currentPage = 0;
        boolean searching = true;

        while (searching) {
            clearScreen();
            final int page = currentPage;

            System.out.println(Colors.YELLOW + "⏳ Searching..." + Colors.RESET);

            List<SearchResult> results = executeWithReconnect(gw -> gw.search(query, page));

            clearScreen();

            if (results.isEmpty()) {
                if (currentPage == 0) {
                    System.out.println(
                            Colors.YELLOW + "\n😕 No results found for: " + Colors.BOLD_WHITE + query + Colors.RESET);
                } else {
                    System.out.println(Colors.YELLOW + "\n📭 No more results." + Colors.RESET);
                }
                break;
            }

            System.out.println(Colors.BOLD_CYAN
                    + "\n╔═══════════════════════════════════════════════════════════════════╗" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + " Results for: " + Colors.BOLD_YELLOW
                    + query +
                    String.format("%" + (52 - query.length()) + "s", "") + Colors.BOLD_CYAN + "║" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN + "║" + Colors.WHITE + " Page " + (currentPage + 1) +
                    String.format("%" + 60 + "s", "") + Colors.BOLD_CYAN + "║" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN
                    + "╚═══════════════════════════════════════════════════════════════════╝" + Colors.RESET);

            int resultNum = currentPage * 10 + 1;
            for (SearchResult result : results) {
                System.out.println();
                System.out.println(Colors.BOLD_GREEN + "▸ " + resultNum + ". " + Colors.BOLD_WHITE + result.getTitle()
                        + Colors.RESET);
                System.out.println(Colors.CYAN + "  🔗 " + result.getUrl() + Colors.RESET);
                System.out.println(Colors.WHITE + "  " + result.getSnippet() + Colors.RESET);
                System.out
                        .println(Colors.YELLOW + "  ⭐ Relevance: " + result.getRelevance() + " inlinks" + Colors.RESET);
                resultNum++;
            }

            System.out.println();
            System.out.println(Colors.BOLD_CYAN + "═══════════════════════════════════════════════════════════════════"
                    + Colors.RESET);
            System.out.println(Colors.GREEN + "[N]" + Colors.WHITE + "ext page  " +
                    Colors.GREEN + "[P]" + Colors.WHITE + "revious page  " +
                    Colors.RED + "[Q]" + Colors.WHITE + "uit search" + Colors.RESET);
            System.out.print(Colors.YELLOW + "\n➤ Choice: " + Colors.RESET);
            String choice = scanner.nextLine().trim().toLowerCase();

            switch (choice) {
                case "n":
                    currentPage++;
                    break;
                case "p":
                    if (currentPage > 0) {
                        currentPage--;
                    } else {
                        System.out.println(Colors.YELLOW + "\n⚠ Already on first page!" + Colors.RESET);
                    }
                    break;
                case "q":
                    searching = false;
                    break;
                default:
                    System.out.println(Colors.RED + "❌ Invalid choice!" + Colors.RESET);
            }
        }
    }

    private void getPagesPointingTo() throws Exception {
        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "     PAGES POINTING TO URL         "
                + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
        System.out.println();
        System.out.print(Colors.CYAN + "🔗 Enter URL: " + Colors.RESET);
        String url = scanner.nextLine().trim();

        if (url.isEmpty()) {
            System.out.println(Colors.RED + "❌ URL cannot be empty!" + Colors.RESET);
            return;
        }

        final String finalUrl = url;

        System.out.println(Colors.YELLOW + "\n⏳ Searching for inlinks..." + Colors.RESET);

        List<String> inlinks = executeWithReconnect(gw -> gw.getPagesPointingTo(finalUrl));

        System.out.println();
        if (inlinks.isEmpty()) {
            System.out.println(Colors.YELLOW + "😕 No pages point to: " + Colors.CYAN + url + Colors.RESET);
        } else {
            System.out
                    .println(Colors.BOLD_GREEN + "✓ Found " + inlinks.size() + " page(s) pointing to:" + Colors.RESET);
            System.out.println(Colors.CYAN + "  " + url + Colors.RESET);
            System.out.println();
            for (int i = 0; i < inlinks.size(); i++) {
                System.out.println(Colors.GREEN + "  " + (i + 1) + ". " + Colors.WHITE + inlinks.get(i) + Colors.RESET);
            }
        }
    }

    private void showStatistics() throws Exception {
        clearScreen();
        System.out.println(Colors.YELLOW + "⏳ Loading statistics..." + Colors.RESET);

        SystemStats stats = executeWithReconnect(gw -> gw.getSystemStats());

        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "       SYSTEM STATISTICS           "
                + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
        System.out.println();
        System.out.println(Colors.BOLD_WHITE + stats.toString() + Colors.RESET);
    }

    private void printURLQueue() throws Exception {
        clearScreen();
        System.out.println(Colors.YELLOW + "⏳ Loading queue..." + Colors.RESET);

        String queueStatus = executeWithReconnect(gw -> gw.getURLQueueStatus());

        clearScreen();
        System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "          URL QUEUE                "
                + Colors.BOLD_CYAN + "║" + Colors.RESET);
        System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
        System.out.println();
        System.out.println(Colors.WHITE + queueStatus + Colors.RESET);
    }

    public static void main(String[] args) {
        GoogolClient client = new GoogolClient();
        client.run();
    }
}
