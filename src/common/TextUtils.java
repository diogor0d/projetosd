package common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * TextUtils - Utility methods for text processing
 * Shared utilities to avoid code duplication across components
 */
public class TextUtils {

    /**
     * Loads stopwords from specified files
     * 
     * @param componentId - ID of the component loading stopwords (for logging)
     * @return Set of lowercase stopwords
     */
    public static Set<String> loadStopwords(String componentId) {
        Set<String> stopwords = new HashSet<>();
        String[] stopwordFiles = { "data/stopwords-en.txt", "data/stopwords-pt.txt" };

        for (String filename : stopwordFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim().toLowerCase();
                    if (!word.isEmpty()) {
                        stopwords.add(word);
                    }
                }
                System.out.println("[" + componentId + "] Loaded stopwords from: " + filename);
            } catch (Exception e) {
                System.err.println("[" + componentId + "] Warning: Could not load stopwords from " + filename + ": "
                        + e.getMessage());
            }
        }

        return stopwords;
    }
}
