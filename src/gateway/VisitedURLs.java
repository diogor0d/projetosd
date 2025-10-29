package gateway;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Conjunto partilhado de URLs visitados
 * Partilhado entre todos os Downloaders para evitar processamento duplicado
 */
public class VisitedURLs {

    private Set<String> visited;

    public VisitedURLs() {
        this.visited = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Verifica se um URL já foi visitado
     * 
     * @param url O URL a verificar
     * @return true se já foi visitado, false caso contrário
     */
    public boolean isVisited(String url) {
        return visited.contains(url);
    }

    /**
     * Marca um URL como visitado
     * 
     * @param url O URL a marcar como visitado
     * @return true se foi adicionado (não estava visitado), false se já estava
     *         visitado
     */
    public boolean markAsVisited(String url) {
        boolean added = visited.add(url);
        if (added) {
            // System.out.println("URL marked as visited: " + url + " (total visited: " +
            // visited.size() + ")");
        }
        return added;
    }

    /**
     * Número total de URLs visitados
     * 
     * @return O tamanho do conjunto de URLs visitados
     */
    public int size() {
        return visited.size();
    }

    /**
     * Verifica se nenhum URL foi visitado ainda
     * 
     * @return true se nenhum URL foi visitado
     */
    public boolean isEmpty() {
        return visited.isEmpty();
    }

    /**
     * Limpa todos os URLs visitados (útil para testes)
     */
    public void clear() {
        visited.clear();
        System.out.println("Visited URLs cleared");
    }
}
