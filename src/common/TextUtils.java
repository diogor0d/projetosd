package common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * TextUtils - metodos auxiliares para o tratamento de texto
 * metodos para evitar duplicação e codigo entre classes
 */
public class TextUtils {

    /**
     * Carrega stopwords atraves dos ficheiros especificados
     *
     * @param componentId - ID do componente a carregar stopwords (para logging)
     * @return Conjunto de stopwords em minusculas
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
