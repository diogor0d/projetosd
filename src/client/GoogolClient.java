package client;

import common.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Scanner;

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
            running = false;
            if (scanner != null) {
                scanner.close();
            }
            System.out.println("\n\nGoodbye!");
        }));
    }

    /**
     * Attempts to connect to gateway indefinitely with retry logic
     */
    private void connectToGateway() {
        System.out.println("Attempting to connect to Gateway...");

        while (!connected) {
            try {
                IGateway tempGateway = (IGateway) Naming.lookup("rmi://localhost/Gateway");
                // Verify connection is alive
                tempGateway.getSystemStats();

                gateway = tempGateway; // Update reference
                connected = true;
                System.out.println("✓ Connected to Googol Gateway");

            } catch (Exception e) {
                System.err.println("Connection failed. Retrying in 3 seconds...");
                try {
                    Thread.sleep(3000);
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
                System.err.println("\n⚠ Lost connection to Gateway!");
                connected = false;
                gateway = null; // Clear old reference
                System.out.println("Reconnecting...");
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
                        break;
                    default:
                        System.out.println("Invalid option!");
                }

                System.out.println();

            } catch (java.util.NoSuchElementException e) {
                // Scanner closed (Ctrl+C pressed) - exit gracefully
                break;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                System.out.println();
            }
        }

        if (scanner != null && !Thread.currentThread().isInterrupted()) {
            scanner.close();
        }
        if (running) {
            System.out.println("Goodbye!");
        }
    }

    private void printMenu() {
        System.out.println("\n========== GOOGOL SEARCH ENGINE ==========");
        System.out.println("1. Index new URL");
        System.out.println("2. Search");
        System.out.println("3. Get pages pointing to URL");
        System.out.println("4. View statistics");
        System.out.println("5. View URL queue");
        System.out.println("0. Exit");
        System.out.print("Choose an option: ");
    }

    private void indexURL() throws Exception {
        System.out.print("Enter URL to index: ");
        String url = scanner.nextLine().trim();

        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        final String finalUrl = url;
        executeWithReconnect(gw -> {
            gw.indexURL(finalUrl);
            return null;
        });

        System.out.println("URL submitted for indexing: " + finalUrl);
    }

    private void search() throws Exception {
        System.out.print("Enter search terms: ");
        String query = scanner.nextLine().trim();

        if (query.isEmpty()) {
            System.out.println("Search query cannot be empty!");
            return;
        }

        int currentPage = 0;
        boolean searching = true;

        while (searching) {
            final int page = currentPage;
            List<SearchResult> results = executeWithReconnect(gw -> gw.search(query, page));

            if (results.isEmpty()) {
                if (currentPage == 0) {
                    System.out.println("\nNo results found for: " + query);
                } else {
                    System.out.println("\nNo more results.");
                }
                break;
            }

            System.out.println("\n========== Results for: " + query + " (Page " + (currentPage + 1) + ") ==========");

            int resultNum = currentPage * 10 + 1;
            for (SearchResult result : results) {
                System.out.println("\n" + resultNum + ". " + result.getTitle());
                System.out.println("   URL: " + result.getUrl());
                System.out.println("   " + result.getSnippet());
                System.out.println("   Relevance: " + result.getRelevance() + " inlinks");
                resultNum++;
            }

            System.out.println("\n[N]ext page | [P]revious page | [Q]uit search");
            System.out.print("Choice: ");
            String choice = scanner.nextLine().trim().toLowerCase();

            switch (choice) {
                case "n":
                    currentPage++;
                    break;
                case "p":
                    if (currentPage > 0) {
                        currentPage--;
                    } else {
                        System.out.println("Already on first page!");
                    }
                    break;
                case "q":
                    searching = false;
                    break;
                default:
                    System.out.println("Invalid choice!");
            }
        }
    }

    private void getPagesPointingTo() throws Exception {
        System.out.print("Enter URL: ");
        String url = scanner.nextLine().trim();

        final String finalUrl = url;
        List<String> inlinks = executeWithReconnect(gw -> gw.getPagesPointingTo(finalUrl));

        if (inlinks.isEmpty()) {
            System.out.println("\nNo pages point to: " + url);
        } else {
            System.out.println("\nPages pointing to " + url + ":");
            for (int i = 0; i < inlinks.size(); i++) {
                System.out.println((i + 1) + ". " + inlinks.get(i));
            }
        }
    }

    private void showStatistics() throws Exception {
        SystemStats stats = executeWithReconnect(gw -> gw.getSystemStats());
        System.out.println("\n" + stats.toString());
    }

    private void printURLQueue() throws Exception {
        String queueStatus = executeWithReconnect(gw -> gw.getURLQueueStatus());
        System.out.println(queueStatus);
    }

    public static void main(String[] args) {
        GoogolClient client = new GoogolClient();
        client.run();
    }
}
