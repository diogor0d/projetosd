package queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Fila de URLs para serem processados pelos Downloaders
 */
public class URLQueue {

    private BlockingQueue<String> queue;

    public URLQueue() {
        this.queue = new LinkedBlockingQueue<>();
    }

    public void addURL(String url) {
        try {
            queue.put(url);
            System.out.println("URL added to queue: " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to add URL to queue: " + e.getMessage());
        }
    }

    public String getNextURL() throws InterruptedException {
        return queue.take();
    }

    public String pollURL(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
