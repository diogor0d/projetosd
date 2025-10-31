package gateway;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fila de URLs para serem processados pelos Downloaders
 * URLs adicionados por utilizadores têm prioridade
 */
public class URLQueue {

    private PriorityBlockingQueue<PriorityURL> queue;
    private Set<String> urlsInQueue;

    public URLQueue() {
        this.queue = new PriorityBlockingQueue<>();
        this.urlsInQueue = ConcurrentHashMap.newKeySet();
    }

    /**
     * Adiciona URL com prioridade normal (descoberto por crawling)
     */
    public void addURL(String url) {
        addURL(url, false);
    }

    /**
     * Adiciona URL com prioridade especificada
     * 
     * @param url          O URL a adicionar
     * @param highPriority true se for submetido por utilizador, false se descoberto
     *                     automaticamente
     */
    public void addURL(String url, boolean highPriority) {
        // Only add if URL is not already in queue
        if (urlsInQueue.add(url)) {
            PriorityURL priorityURL = new PriorityURL(url, highPriority);
            queue.put(priorityURL);
        }
    }

    public String getNextURL() throws InterruptedException {
        PriorityURL priorityURL = queue.take();
        urlsInQueue.remove(priorityURL.getUrl());
        return priorityURL.getUrl();
    }

    public String pollURL(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        PriorityURL priorityURL = queue.poll(timeout, unit);
        if (priorityURL != null) {
            urlsInQueue.remove(priorityURL.getUrl());
        }
        return priorityURL != null ? priorityURL.getUrl() : null;
    }

    public int size() {
        return urlsInQueue.size();
    }

    public boolean isEmpty() {
        return urlsInQueue.isEmpty();
    }

    /**
     * Imprime o conteúdo atual da fila (para debug)
     * Mostra apenas os primeiros 20 elementos
     * NOTA: Este método NÃO consome os elementos - apenas os visualiza
     */
    public void printQueue() {
        System.out.println("\n=== URL Queue Status ===");
        System.out.println("Queue size: " + queue.size());

        if (queue.isEmpty()) {
            System.out.println("Queue is empty.");
        } else {
            int totalSize = queue.size();
            int maxToShow = Math.min(20, totalSize);

            System.out.println(
                    "Queue contents (showing first " + maxToShow + " of " + totalSize + " in priority order):");
            int index = 1;
            for (PriorityURL priorityURL : queue) {
                if (index > maxToShow) {
                    System.out.println("  ... (" + (totalSize - maxToShow) + " more entries)");
                    break;
                }
                System.out.println(String.format("  %d. [%s] %s",
                        index++,
                        priorityURL.highPriority ? "HIGH" : "NORMAL",
                        priorityURL.url));
            }
        }
        System.out.println("========================\n");
    }

    /**
     * Retorna o conteúdo da fila como String (para RMI)
     * Mostra apenas os primeiros 20 elementos
     * NOTA: Este método NÃO consome os elementos - apenas os visualiza
     */
    public String getQueueAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== URL Queue Status ===\n");
        sb.append("Queue size: ").append(queue.size()).append("\n");

        if (queue.isEmpty()) {
            sb.append("Queue is empty.\n");
        } else {
            int totalSize = queue.size();
            int maxToShow = Math.min(20, totalSize);

            sb.append("Queue contents (showing first ").append(maxToShow)
                    .append(" of ").append(totalSize).append(" in priority order):\n");
            int index = 1;
            for (PriorityURL priorityURL : queue) {
                if (index > maxToShow) {
                    sb.append("  ... (").append(totalSize - maxToShow).append(" more entries)\n");
                    break;
                }
                sb.append(String.format("  %d. [%s] %s\n",
                        index++,
                        priorityURL.highPriority ? "HIGH" : "NORMAL",
                        priorityURL.url));
            }
        }
        sb.append("========================\n");
        return sb.toString();
    }

    /**
     * Classe interna para URLs com prioridade
     */
    private static class PriorityURL implements Comparable<PriorityURL> {
        private final String url;
        private final boolean highPriority;
        private final long timestamp;

        public PriorityURL(String url, boolean highPriority) {
            this.url = url;
            this.highPriority = highPriority;
            this.timestamp = System.currentTimeMillis();
        }

        public String getUrl() {
            return url;
        }

        @Override
        public int compareTo(PriorityURL other) {
            // High priority first
            if (this.highPriority != other.highPriority) {
                return this.highPriority ? -1 : 1;
            }
            // If same priority, FIFO (earlier timestamp first)
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
}
