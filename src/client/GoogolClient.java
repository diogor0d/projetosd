package client;

import common.*;
import java.rmi.Naming;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente RMI para interagir com o sistema Googol
 */
public class GoogolClient {

    private IGateway gateway;
    private Scanner scanner;

    public GoogolClient() throws Exception {
        // Conecta à Gateway
        gateway = (IGateway) Naming.lookup("rmi://localhost/Gateway");
        scanner = new Scanner(System.in);
        System.out.println("Connected to Googol Gateway");
    }

    public void run() {
        boolean running = true;

        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();

            try {
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
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println();
        }

        scanner.close();
        System.out.println("Goodbye!");
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

        gateway.indexURL(url);
        System.out.println("URL submitted for indexing: " + url);
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
            List<SearchResult> results = gateway.search(query, currentPage);

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

        List<String> inlinks = gateway.getPagesPointingTo(url);

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
        SystemStats stats = gateway.getSystemStats();
        System.out.println("\n" + stats.toString());
    }

    private void printURLQueue() throws Exception {
        String queueStatus = gateway.getURLQueueStatus();
        System.out.println(queueStatus);
    }

    public static void main(String[] args) {
        try {
            GoogolClient client = new GoogolClient();
            client.run();
        } catch (Exception e) {
            System.err.println("Client exception:");
            e.printStackTrace();
        }
    }
}
