package common;

import java.io.Serializable;

/**
 * Estatísticas de um Storage Barrel
 */
public class BarrelStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private String barrelId;
    private int indexSize;
    private double avgResponseTime; // Em milissegundos
    private boolean active;
    private boolean syncing; // guarda o estado de sincronizacao do barril
    private String syncingFromPeer; // Qual peer está a fornecer dados de sincronização (null se não estiver a
                                    // sincronizar)
    private int syncProgress; // Percentagem de progresso da sincronização (0-100)

    public BarrelStats(String barrelId, int indexSize, double avgResponseTime) {
        this.barrelId = barrelId;
        this.indexSize = indexSize;
        this.avgResponseTime = avgResponseTime;
        this.active = true;
        this.syncing = false;
        this.syncingFromPeer = null;
        this.syncProgress = 0;
    }

    // Constructor with sync information
    public BarrelStats(String barrelId, int indexSize, double avgResponseTime,
            boolean syncing, String syncingFromPeer, int syncProgress) {
        this.barrelId = barrelId;
        this.indexSize = indexSize;
        this.avgResponseTime = avgResponseTime;
        this.active = true;
        this.syncing = syncing;
        this.syncingFromPeer = syncingFromPeer;
        this.syncProgress = syncProgress;
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

    public boolean isSyncing() {
        return syncing;
    }

    public String getSyncingFromPeer() {
        return syncingFromPeer;
    }

    public int getSyncProgress() {
        return syncProgress;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Barrel ").append(barrelId);
        sb.append(" | Index Size: ").append(indexSize);
        sb.append(" | Avg Response: ").append(String.format("%.3f", avgResponseTime)).append("ms");

        if (syncing && syncingFromPeer != null) {
            sb.append(" | Status: SYNCING from ").append(syncingFromPeer);
            sb.append(" (").append(syncProgress).append("%)");
        } else if (!active) {
            sb.append(" | Status: INACTIVE");
        } else {
            sb.append(" | Status: READY");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        BarrelStats that = (BarrelStats) obj;

        if (indexSize != that.indexSize)
            return false;
        if (Double.compare(that.avgResponseTime, avgResponseTime) != 0)
            return false;
        if (active != that.active)
            return false;
        if (syncing != that.syncing)
            return false;
        if (syncProgress != that.syncProgress)
            return false;
        if (!barrelId.equals(that.barrelId))
            return false;
        return syncingFromPeer != null ? syncingFromPeer.equals(that.syncingFromPeer) : that.syncingFromPeer == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = barrelId.hashCode();
        result = 31 * result + indexSize;
        temp = Double.doubleToLongBits(avgResponseTime);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (active ? 1 : 0);
        result = 31 * result + (syncing ? 1 : 0);
        result = 31 * result + (syncingFromPeer != null ? syncingFromPeer.hashCode() : 0);
        result = 31 * result + syncProgress;
        return result;
    }
}
