package common;

import java.io.Serializable;

/**
 * Estatísticas de um Storage Barrel
 */
public class BarrelStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private String barrelId;
    private int indexSize;
    private double avgResponseTime; // Em décimas de segundo
    private boolean active;

    public BarrelStats(String barrelId, int indexSize, double avgResponseTime) {
        this.barrelId = barrelId;
        this.indexSize = indexSize;
        this.avgResponseTime = avgResponseTime;
        this.active = true;
    }

    public String getBarrelId() {
        return barrelId;
    }

    public int getIndexSize() {
        return indexSize;
    }

    public double getAvgResponseTime() {
        return avgResponseTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "Barrel " + barrelId +
                " | Index Size: " + indexSize +
                " | Avg Response: " + String.format("%.1f", avgResponseTime) + "s" +
                " | Status: " + (active ? "ACTIVE" : "INACTIVE");
    }
}
