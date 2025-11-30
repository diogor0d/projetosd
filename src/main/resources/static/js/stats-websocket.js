// websocket para atualizações de estatísticas em tempo real
(function() {
    'use strict';
    
    let stompClient = null;
    let connected = false;
    
    // conecta ao WebSocket
    function connect() {
        const socket = new SockJS('/ws-stats');
        stompClient = Stomp.over(socket);
        
        // desabilita debug logs do stomp
        stompClient.debug = null;
        
        stompClient.connect({}, function(frame) {
            console.log('WebSocket conectado:', frame);
            connected = true;
            updateConnectionIndicator(true);
            
            // subscreve ao tópico de estatísticas
            stompClient.subscribe('/topic/stats', function(message) {
                const stats = JSON.parse(message.body);
                updateStatsUI(stats);
            });
        }, function(error) {
            console.error('Erro na conexão WebSocket:', error);
            connected = false;
            updateConnectionIndicator(false);
            
            // tenta reconectar após 5 segundos
            setTimeout(connect, 5000);
        });
    }
    
    // atualiza indicador de conexão
    function updateConnectionIndicator(isConnected) {
        const indicator = document.getElementById('live-indicator');
        if (indicator) {
            if (isConnected) {
                indicator.style.background = 'var(--success-color)';
                indicator.style.animation = 'pulse 2s infinite';
            } else {
                indicator.style.background = 'var(--error-color)';
                indicator.style.animation = 'none';
            }
        }
    }
    
    // atualiza a UI com as novas estatísticas
    function updateStatsUI(stats) {
        console.log('Estatísticas atualizadas:', stats);
        
        // atualiza valores principais
        updateElement('total-pages', stats.totalIndexedPages);
        updateElement('queue-size', stats.urlQueueSize);
        updateElement('barrel-count', stats.barrelStats ? stats.barrelStats.length : 0);
        
        // atualiza top searches
        if (stats.topSearches && stats.topSearches.length > 0) {
            updateTopSearches(stats.topSearches);
        }
        
        // atualiza informação dos barrels
        if (stats.barrelStats && stats.barrelStats.length > 0) {
            updateBarrelStats(stats.barrelStats);
        }
    }
    
    // atualiza um elemento de texto
    function updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) {
            // anima a mudança
            element.style.transition = 'all 0.3s';
            element.style.transform = 'scale(1.1)';
            element.textContent = value;
            
            setTimeout(() => {
                element.style.transform = 'scale(1)';
            }, 300);
        }
    }
    
    // atualiza a lista de top searches
    function updateTopSearches(searches) {
        const container = document.getElementById('top-searches');
        if (!container) return;
        
        let html = '<ol>';
        searches.forEach(search => {
            html += `<li>${escapeHtml(search)}</li>`;
        });
        html += '</ol>';
        
        container.innerHTML = html;
    }
    
    // atualiza estatísticas dos barrels
    function updateBarrelStats(barrels) {
        const container = document.getElementById('barrel-stats');
        if (!container) return;
        
        let html = '<div>';
        barrels.forEach(barrel => {
            html += `
                <div class="barrel-card">
                    <h3>${escapeHtml(barrel.barrelId)}</h3>
                    <div class="barrel-info">
                        <p>
                            <strong>Páginas indexadas:</strong> 
                            <span>${barrel.indexSize}</span>
                        </p>
                        <p>
                            <strong>Tempo médio de resposta:</strong> 
                            <span>${barrel.avgResponseTime.toFixed(1)} ms</span>
                        </p>
                    </div>
                </div>
            `;
        });
        html += '</div>';
        
        container.innerHTML = html;
    }
    
    // função de escape HTML (reutilizada do app.js)
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
    
    // desconecta ao sair da página
    function disconnect() {
        if (stompClient !== null) {
            stompClient.disconnect();
            connected = false;
            updateConnectionIndicator(false);
            console.log('WebSocket desconectado');
        }
    }
    
    // conecta automaticamente quando a página carrega
    document.addEventListener('DOMContentLoaded', function() {
        // verifica se estamos na página de estatísticas
        if (document.getElementById('stats-container')) {
            connect();
        }
    });
    
    // desconecta ao sair
    window.addEventListener('beforeunload', disconnect);
    
})();
