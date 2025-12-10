package client;

import common.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Scanner;

/**
 * codigos de cor ANSI para cores de terminal
 * desativa automaticamente quando cores nao sao suportadas (e.g., output Maven)
 * define -DforceColor=true ou FORCE_COLOR=1 env var para ativar cores no Maven
 */
class Colors {
    // deteta se cores sao suportadas ou forcadas
    private static final boolean COLORS_ENABLED = "true".equalsIgnoreCase(System.getProperty("forceColor")) ||
            "1".equals(System.getenv("FORCE_COLOR")) ||
            (System.console() != null && System.getenv().get("TERM") != null);

    // metodo auxiliar para retornar codigo de cor ou string vazia
    private static String color(String code) {
        return COLORS_ENABLED ? code : "";
    }

    // reset
    public static final String RESET = color("\033[0m");

    // cores regulares
    public static final String BLACK = color("\033[0;30m");
    public static final String RED = color("\033[0;31m");
    public static final String GREEN = color("\033[0;32m");
    public static final String YELLOW = color("\033[0;33m");
    public static final String BLUE = color("\033[0;34m");
    public static final String PURPLE = color("\033[0;35m");
    public static final String CYAN = color("\033[0;36m");
    public static final String WHITE = color("\033[0;37m");

    // negrito
    public static final String BOLD = color("\033[1m");
    public static final String BOLD_RED = color("\033[1;31m");
    public static final String BOLD_GREEN = color("\033[1;32m");
    public static final String BOLD_YELLOW = color("\033[1;33m");
    public static final String BOLD_BLUE = color("\033[1;34m");
    public static final String BOLD_PURPLE = color("\033[1;35m");
    public static final String BOLD_CYAN = color("\033[1;36m");
    public static final String BOLD_WHITE = color("\033[1;97m");

    // fundo
    public static final String BG_BLUE = color("\033[44m");
    public static final String BG_GREEN = color("\033[42m");
    public static final String BG_RED = color("\033[41m");
}

/**
 * cliente RMI para interagir com o sistema Googol
 */
public class GoogolClient {

    private volatile IGateway gateway; // volatile para reconexao segura entre threads
    private Scanner scanner;
    private volatile boolean connected;
    private volatile boolean running; // controla se o cliente deve continuar em execucao

    public GoogolClient() {
        scanner = new Scanner(System.in);
        connected = false;
        running = true;

        // adiciona um shutdown hook para saida limpa ao pressionar Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // faz flush aos streams de output para evitar problemas Maven/Jansi
                System.out.flush();
                System.err.flush();

                running = false;
                if (scanner != null) {
                    scanner.close();
                }
                // limpa o ecra e apresenta mensagem de despedida
                System.out.print("\033[H\033[2J");
                System.out.flush();
                System.out.println(Colors.BOLD_CYAN + "\n👋 Thank you for using Googol!" + Colors.RESET);
                System.out.println(Colors.CYAN + "Goodbye!\n" + Colors.RESET);
                System.out.flush();
            } catch (Exception e) {
                // suprime erros de output durante shutdown (problemas Maven/Jansi)
            }
        }));
    }

    /**
     * limpa o ecra (funciona na maioria dos terminais)
     */
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /**
     * imprime o banner do Googol
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
        // sleep 1.5s
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * tenta reconectar ao gateway RMI indefinidamente atraves de uma logica de
     * reconexao
     */
    private void connectToGateway() {
        clearScreen();
        printBanner();

        // Load and display RMI configuration
        String gatewayUrl = common.RMIConfig.getClientGatewayRMIUrl();
        System.out.println(Colors.BOLD_CYAN + "═══════════════════════════════════════════" + Colors.RESET);
        common.RMIConfig.printConfig("Client");
        System.out.println(Colors.BOLD_CYAN + "═══════════════════════════════════════════" + Colors.RESET);
        System.out.println();

        System.out.println(Colors.YELLOW + "🔌 Connecting to Gateway at " + gatewayUrl + "..." + Colors.RESET);
        System.out.println();

        try {
            Thread.sleep(500); // delay de 1 segundo
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int attempts = 0;
        while (!connected) {
            try {
                attempts++;
                System.out.print("\r" + Colors.CYAN + "   Attempt " + attempts + "... ");
                System.out.flush();

                IGateway tempGateway = (IGateway) Naming.lookup(gatewayUrl);
                // verifica se a conexao esta funcional
                tempGateway.getSystemStats();

                gateway = tempGateway; // atualiza referencia
                connected = true;

                System.out.print("\r" + Colors.CYAN + "   Attempt " + attempts + "... " + Colors.BOLD_GREEN
                        + "✓ Connected!" + Colors.RESET);
                System.out.println();
                clearScreen();
                printBanner();
                System.out.println(Colors.GREEN + "✓ Successfully connected to Googol Gateway" + Colors.RESET);
                System.out.println(Colors.CYAN + "  Gateway: " + gatewayUrl + Colors.RESET);
                System.out.println();

            } catch (Exception e) {
                System.out.print(Colors.RED + "✗ Failed" + Colors.RESET);
                System.out.print(Colors.YELLOW + " - Retrying in 3 seconds" + Colors.RESET);
                System.out.flush();
                try {
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(1000);
                        System.out.print(".");
                        System.out.flush();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * executa uma operacao no gateway com reconexao automatica em caso de falha
     */
    private <T> T executeWithReconnect(GatewayOperation<T> operation) throws Exception {
        while (true) {
            try {
                if (!connected) {
                    connectToGateway();
                }
                return operation.execute(gateway);

            } catch (RemoteException e) {
                // verifica se se trata de um erro de validacao/logica de negocio (e nao um
                // problema de conexao)
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Invalid URL")
                        || errorMsg.contains("already indexed")
                        || errorMsg.contains("No active barrels"))) {
                    throw e;
                }

                // caso contrario, trata-se como um problema de conexao - tenta reconectar
                System.out.println();
                System.out.println(Colors.BOLD_RED + "⚠ Connection Lost!" + Colors.RESET);
                connected = false;
                gateway = null; // limpa referencia antiga
                System.out.println(Colors.YELLOW + "🔄 Reconnecting..." + Colors.RESET);
                connectToGateway();
                // tenta operacao apos reconexao
            }
        }
    }

    /**
     * interface funcional para operacoes do gateway
     */
    @FunctionalInterface
    private interface GatewayOperation<T> {
        T execute(IGateway gateway) throws Exception;
    }

    public void run() {
        // conexao inicial
        connectToGateway();

        while (running) {
            try {
                printMenu();

                // verifica se scanner tem input disponivel (previne excepcao no Ctrl+C)
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
                        // scanner fechado - sai graciosamente
                        running = false;
                        break;
                    }
                }

            } catch (java.util.NoSuchElementException | IllegalStateException e) {
                // scanner fechado (Ctrl+C pressionado) - sai graciosamente
                running = false;
                break;
            } catch (Exception e) {
                if (running) {
                    System.out.println(Colors.RED + "❌ Error: " + e.getMessage() + Colors.RESET);
                    System.out.println();
                }
            }
        }

        // fecho limpo - shutdown hook vai tratar da apresentacao
        if (scanner != null && !Thread.currentThread().isInterrupted()) {
            try {
                scanner.close();
            } catch (Exception e) {
                // ignora
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
            // trata erros de validacao do Gateway
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Invalid URL")) {
                System.out.println(Colors.BOLD_RED + "\n❌ Invalid URL!" + Colors.RESET);
                System.out.println(Colors.RED + "   " + errorMsg + Colors.RESET);
            } else if (errorMsg != null && errorMsg.contains("already indexed")) {
                System.out.println(Colors.BOLD_YELLOW + "\n⚠ URL Already Indexed!" + Colors.RESET);
                System.out.println(
                        Colors.YELLOW + "   This URL has already been processed by the system." + Colors.RESET);
            } else {
                throw e; // relanca outras excecoes
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

            SearchResponse searchResponse;
            try {
                searchResponse = executeWithReconnect(gw -> gw.search(query, page));
            } catch (RemoteException e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("No active barrels")) {
                    // mensagem amigavel em vez de reconectar ou despejar excepcao sem nada
                    // adicional
                    clearScreen();
                    System.out.println(Colors.YELLOW + "\n😕 No indexed data available yet." + Colors.RESET);
                    System.out.println(Colors.YELLOW
                            + "   The system currently has no barrels/indexed pages. Please try again later."
                            + Colors.RESET);
                    System.out.println();
                    System.out.print(Colors.YELLOW + "Press Enter to return to the menu..." + Colors.RESET);
                    try {
                        if (scanner.hasNextLine()) {
                            scanner.nextLine();
                        }
                    } catch (Exception ie) {
                        // ignora problemas do scanner
                    }
                    break; // retorna ao menu principal
                }
                throw e;
            }

            List<SearchResult> results = searchResponse.getResults();
            int totalCount = searchResponse.getTotalCount();

            clearScreen();

            if (results.isEmpty()) {
                if (currentPage == 0) {
                    System.out.println(
                            Colors.YELLOW + "\n😕 No results found for: " + Colors.BOLD_WHITE + query + Colors.RESET);
                } else {
                    System.out.println(Colors.YELLOW + "\n📭 No more results." + Colors.RESET);
                }
                // pausa para que o utilizador possa ler a mensagem antes de retornar ao menu
                System.out.println();
                System.out.print(Colors.YELLOW + "Press Enter to return to the menu..." + Colors.RESET);
                try {
                    if (scanner.hasNextLine()) {
                        scanner.nextLine();
                    }
                } catch (Exception ie) {
                    // ignora problemas do scanner e continua
                }
                break;
            }

            System.out.println(Colors.BOLD_CYAN
                    + "\n╔═══════════════════════════════════════════════════════════════════╗" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + " Results for: " + Colors.BOLD_YELLOW
                    + query +
                    String.format("%" + (52 - query.length()) + "s", "") + Colors.BOLD_CYAN + "║" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN + "║" + Colors.WHITE + " About " + Colors.BOLD_YELLOW + totalCount
                    + Colors.WHITE + " results" +
                    String.format("%" + (53 - String.valueOf(totalCount).length()) + "s", "") + Colors.BOLD_CYAN + "║"
                    + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN + "║" + Colors.WHITE + " Page " + (currentPage + 1) + " of "
                    + searchResponse.getTotalPages() +
                    String.format("%" + (56 - String.valueOf(currentPage + 1).length()
                            - String.valueOf(searchResponse.getTotalPages()).length()) + "s", "")
                    + Colors.BOLD_CYAN + "║" + Colors.RESET);
            System.out.println(Colors.BOLD_CYAN
                    + "╚═══════════════════════════════════════════════════════════════════╝" + Colors.RESET);

            int resultNum = currentPage * 10 + 1;
            for (SearchResult result : results) {
                System.out.println();
                System.out.println(Colors.BOLD_GREEN + "▸ " + resultNum + ". " + Colors.BOLD_WHITE + result.getTitle()
                        + Colors.RESET);
                System.out.println(Colors.CYAN + "  🔗 " + result.getUrl() + Colors.RESET);
                System.out.println(Colors.WHITE + "  " + result.getSnippet() + Colors.RESET);
                System.out.println(Colors.PURPLE + "  📊 Score: " + String.format("%.4f", result.getTfIdfScore())
                        + " (TF-IDF)" + Colors.RESET);
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

        if (!url.startsWith("http")) {
            url = "https://" + url;
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
        // regista um listener de estatisticas remoto e mostra atualizacoes em tempo
        // real
        clearScreen();
        System.out.println(Colors.YELLOW + "⏳ Subscribing to live statistics (press Enter to stop)" + Colors.RESET);

        // cria e exporta um listener remoto
        IStatsListener listener = new IStatsListenerImpl();

        try {
            executeWithReconnect(gw -> {
                gw.registerStatsListener(listener);
                return null;
            });

            // aguarda ate o utilizador pressionar Enter para parar atualizacoes ao vivo
            System.out.println();
            scanner.nextLine();

        } finally {
            // remove registo e unexporta listener
            try {
                executeWithReconnect(gw -> {
                    gw.unregisterStatsListener(listener);
                    return null;
                });
            } catch (Exception e) {
                // ignora
            }

            try {
                UnicastRemoteObject.unexportObject(listener, true);
            } catch (Exception e) {
                // ignora
            }
        }
    }

    /**
     * implementacao simples de listener remoto que imprime estatisticas recebidas
     * em tempo
     * real.
     */
    private class IStatsListenerImpl extends UnicastRemoteObject implements IStatsListener {
        protected IStatsListenerImpl() throws RemoteException {
            super();
        }

        @Override
        public void updateStats(SystemStats stats) throws RemoteException {
            // imprime estatisticas numa regiao limpa do ecra
            synchronized (System.out) {
                clearScreen();
                System.out.println(Colors.BOLD_CYAN + "\n╔═══════════════════════════════════════╗" + Colors.RESET);
                System.out.println(Colors.BOLD_CYAN + "║" + Colors.BOLD_WHITE + "       SYSTEM STATISTICS           "
                        + Colors.BOLD_CYAN + "║" + Colors.RESET);
                System.out.println(Colors.BOLD_CYAN + "╚═══════════════════════════════════════╝" + Colors.RESET);
                System.out.println();
                System.out.println(Colors.BOLD_WHITE + stats.toString() + Colors.RESET);
                System.out.println();
                System.out.print(Colors.YELLOW + "Press Enter to stop listening to live statistics from the gateway..."
                        + Colors.RESET);
            }
        }
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
