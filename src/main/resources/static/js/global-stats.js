// Global stats widget - WebSocket connection for live stats on all pages
(function() {
    'use strict';
    
    let stompClient = null;
    let connected = false;
    
    // Connect to WebSocket
    function connect() {
        console.log('[Global Stats] Attempting to connect to WebSocket...');
        const socket = new SockJS('/ws-stats');
        stompClient = Stomp.over(socket);
        
        // Disable debug logs
        stompClient.debug = null;
        
        stompClient.connect({}, function(frame) {
            console.log('[Global Stats] ✅ Connected successfully!');
            connected = true;
            updateConnectionIndicator(true);
            
            // Subscribe to stats topic
            stompClient.subscribe('/topic/stats', function(message) {
                console.log('[Global Stats] 📊 Received message:', message.body);
                const stats = JSON.parse(message.body);
                updateGlobalStatsBar(stats);
                updateStatsPage(stats);
            });
            
            console.log('[Global Stats] 📡 Subscribed to /topic/stats');
            
            // Fetch initial stats immediately
            fetchInitialStats();
        }, function(error) {
            console.error('[Global Stats] ❌ Connection error:', error);
            connected = false;
            updateConnectionIndicator(false);
            
            // Retry connection after 5 seconds
            console.log('[Global Stats] 🔄 Retrying in 5 seconds...');
            setTimeout(connect, 5000);
        });
    }
    
    // Fetch initial stats via REST API
    function fetchInitialStats() {
        console.log('[Global Stats] Fetching initial stats...');
        fetch('/api/stats')
            .then(response => response.json())
            .then(stats => {
                console.log('[Global Stats] 📊 Initial stats received:', stats);
                updateGlobalStatsBar(stats);
                updateStatsPage(stats);
            })
            .catch(error => {
                console.error('[Global Stats] ❌ Error fetching initial stats:', error);
            });
    }
    
    // Update connection indicator
    function updateConnectionIndicator(isConnected) {
        const indicator = document.getElementById('global-live-indicator');
        if (indicator) {
            if (isConnected) {
                indicator.style.background = 'var(--success-color, #10b981)';
                indicator.style.animation = 'pulse 2s infinite';
            } else {
                indicator.style.background = 'var(--error-color, #ef4444)';
                indicator.style.animation = 'none';
            }
        }
    }
    
    // Update the global stats bar
    function updateGlobalStatsBar(stats) {
        console.log('[Global Stats] Updating bar with:', stats);
        updateElement('global-total-pages', stats.totalIndexedPages);
        updateElement('global-queue-size', stats.urlQueueSize);
        updateElement('global-barrel-count', stats.barrelStats ? stats.barrelStats.length : 0);
    }
    
    // Update the stats page if we're on it
    function updateStatsPage(stats) {
        // Check if we're on the stats page
        const statsContainer = document.getElementById('stats-container');
        if (!statsContainer) return;
        
        console.log('[Global Stats] Updating stats page with:', stats);
        
        // Update main stats
        updateElement('total-pages', stats.totalIndexedPages);
        updateElement('queue-size', stats.urlQueueSize);
        updateElement('barrel-count', stats.barrelStats ? stats.barrelStats.length : 0);
        
        // Update connection indicator
        const indicator = document.getElementById('live-indicator');
        if (indicator) {
            indicator.style.background = 'var(--success-color, #10b981)';
            indicator.style.animation = 'pulse 2s infinite';
        }
        
        // Update top searches
        if (stats.topSearches && stats.topSearches.length > 0) {
            updateTopSearches(stats.topSearches);
        }
        
        // Update barrel stats
        if (stats.barrelStats && stats.barrelStats.length > 0) {
            updateBarrelStats(stats.barrelStats);
        }
    }
    
    // Update top searches list
    function updateTopSearches(searches) {
        const container = document.getElementById('top-searches');
        if (!container) return;
        
        let html = '<ol style="padding-left: 1.5rem; color: var(--text-secondary)">';
        searches.forEach(search => {
            html += `<li style="margin-bottom: 0.5rem; color: var(--text-primary)">${escapeHtml(search)}</li>`;
        });
        html += '</ol>';
        
        container.innerHTML = html;
    }
    
    // Update barrel statistics
    function updateBarrelStats(barrels) {
        const container = document.getElementById('barrel-stats');
        if (!container) return;
        
        let html = '';
        barrels.forEach(barrel => {
            html += `
                <div class="barrel-card">
                    <h3 style="color: var(--accent)">${escapeHtml(barrel.barrelId)}</h3>
                    <div style="display: flex; justify-content: space-between; margin-top: 0.5rem; font-size: 0.9rem;">
                        <span>Pages: <strong>${barrel.indexSize}</strong></span>
                        <span>Avg Latency: <strong>${barrel.avgResponseTime.toFixed(1)} ms</strong></span>
                    </div>
                </div>
            `;
        });
        
        container.innerHTML = html;
    }
    
    // Escape HTML to prevent XSS
    function escapeHtml(text) {
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return String(text).replace(/[&<>"']/g, m => map[m]);
    }
    
    // Update an element with animation
    function updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) {
            const oldValue = element.textContent;
            if (oldValue !== String(value)) {
                element.style.transition = 'all 0.3s';
                element.style.transform = 'scale(1.15)';
                element.style.color = 'var(--accent-color, #3b82f6)';
                element.textContent = value;
                
                setTimeout(() => {
                    element.style.transform = 'scale(1)';
                    element.style.color = '';
                }, 300);
            }
        }
    }
    
    // Disconnect on page unload
    function disconnect() {
        if (stompClient !== null) {
            stompClient.disconnect();
            connected = false;
            updateConnectionIndicator(false);
        }
    }
    
    // Auto-connect when page loads (skip on stats page - it has its own WebSocket)
    document.addEventListener('DOMContentLoaded', function() {
        console.log('[Global Stats] 🚀 Page loaded, initializing...');
        // Always connect - we handle both global bar and stats page now
        connect();
    });
    
    // Disconnect on page unload
    window.addEventListener('beforeunload', disconnect);
    
})();
