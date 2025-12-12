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
        // If URL not present, add normally
        synchronized (this) {
            if (urlsInQueue.add(url)) {
                PriorityURL priorityURL = new PriorityURL(url, highPriority);
                queue.put(priorityURL);
                return;
            }
            // URL already present. If caller requests high priority, try to upgrade
            if (highPriority) {
                // Iterate queue to find existing entry and upgrade if it is normal
                for (PriorityURL pu : queue) {
                    if (pu.url.equals(url) && !pu.highPriority) {
                        // remove old (normal) entry and insert a new high-priority one
                        boolean removed = queue.remove(pu);
                        if (removed) {
                            PriorityURL upgraded = new PriorityURL(url, true);
                            queue.put(upgraded);
                        }
                        break;
                    }
                }
            }
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
            System.out.println("========================\n");
            return;
        }

        // PriorityBlockingQueue iterator does NOT guarantee ordered traversal.
        // To show the queue in priority order we copy to an array and sort.
        PriorityURL[] arr = queue.toArray(new PriorityURL[0]);
        java.util.Arrays.sort(arr);

        int totalSize = arr.length;
        int maxToShow = Math.min(20, totalSize);

        System.out.println(
                "Queue contents (showing first " + maxToShow + " of " + totalSize + " in priority order):");
        for (int i = 0; i < maxToShow; i++) {
            PriorityURL priorityURL = arr[i];
            System.out.println(String.format("  %d. [%s] %s",
                    i + 1,
                    priorityURL.highPriority ? "HIGH" : "NORMAL",
                    priorityURL.url));
        }
        if (totalSize > maxToShow) {
            System.out.println("  ... (" + (totalSize - maxToShow) + " more entries)");
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
            sb.append("========================\n");
            return sb.toString();
        }

        PriorityURL[] arr = queue.toArray(new PriorityURL[0]);
        java.util.Arrays.sort(arr);

        int totalSize = arr.length;
        int maxToShow = Math.min(20, totalSize);

        sb.append("Queue contents (showing first ").append(maxToShow)
                .append(" of ").append(totalSize).append(" in priority order):\n");
        for (int i = 0; i < maxToShow; i++) {
            PriorityURL priorityURL = arr[i];
            sb.append(String.format("  %d. [%s] %s\n",
                    i + 1,
                    priorityURL.highPriority ? "HIGH" : "NORMAL",
                    priorityURL.url));
        }
        if (totalSize > maxToShow) {
            sb.append("  ... (" + (totalSize - maxToShow) + " more entries)\n");
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
