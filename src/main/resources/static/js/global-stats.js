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
    
    // Auto-connect when page loads
    document.addEventListener('DOMContentLoaded', function() {
        console.log('[Global Stats] 🚀 Page loaded, initializing...');
        connect();
    });
    
    // Disconnect on page unload
    window.addEventListener('beforeunload', disconnect);
    
})();
