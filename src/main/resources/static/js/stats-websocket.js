// websocket para atualizações de estatísticas em tempo real
(function() {
    'use strict';
    
    let stompClient = null;
    let connected = false;
    
    // conecta ao WebSocket
    function connect() {
        console.log('[Stats WebSocket] Tentando conectar...');
        const socket = new SockJS('/ws-stats');
        stompClient = Stomp.over(socket);
        
        // habilita debug logs do stomp temporariamente
        stompClient.debug = function(str) {
            console.log('[STOMP Debug]', str);
        };
        
        stompClient.connect({}, function(frame) {
            console.log('[Stats WebSocket] ✅ Conectado com sucesso!', frame);
            connected = true;
            updateConnectionIndicator(true);
            
            // subscreve ao tópico de estatísticas
            stompClient.subscribe('/topic/stats', function(message) {
                console.log('[Stats WebSocket] 📊 Mensagem recebida:', message.body);
                const stats = JSON.parse(message.body);
                console.log('[Stats WebSocket] 📊 Estatísticas parseadas:', stats);
                updateStatsUI(stats);
            });
            
            console.log('[Stats WebSocket] 📡 Subscrito ao tópico /topic/stats');
        }, function(error) {
            console.error('[Stats WebSocket] ❌ Erro na conexão:', error);
            connected = false;
            updateConnectionIndicator(false);
            
            // tenta reconectar após 5 segundos
            console.log('[Stats WebSocket] 🔄 Tentando reconectar em 5 segundos...');
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
        console.log('[Stats UI] 🔄 Atualizando interface com:', stats);
        
        // atualiza valores principais
        updateElement('total-pages', stats.totalIndexedPages);
        updateElement('queue-size', stats.urlQueueSize);
        updateElement('barrel-count', stats.barrelStats ? stats.barrelStats.length : 0);
        
        console.log('[Stats UI] ✅ Valores principais atualizados');
        
        // atualiza top searches
        if (stats.topSearches && stats.topSearches.length > 0) {
            updateTopSearches(stats.topSearches);
            console.log('[Stats UI] ✅ Top searches atualizadas');
        }
        
        // atualiza informação dos barrels
        if (stats.barrelStats && stats.barrelStats.length > 0) {
            updateBarrelStats(stats.barrelStats);
            console.log('[Stats UI] ✅ Barrel stats atualizadas');
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
        console.log('[Stats WebSocket] 🚀 DOM carregado');
        // verifica se estamos na página de estatísticas
        const statsContainer = document.getElementById('stats-container');
        if (statsContainer) {
            console.log('[Stats WebSocket] 📊 Página de estatísticas detectada, iniciando conexão...');
            connect();
        } else {
            console.log('[Stats WebSocket] ⏭️  Não é a página de estatísticas, WebSocket não será iniciado');
        }
    });
    
    // desconecta ao sair
    window.addEventListener('beforeunload', disconnect);
    
})();
