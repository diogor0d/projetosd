// googol - javascript principal
console.log('Googol Web Application initialized');

// utilitário para formatar números
function formatNumber(num) {
    return new Intl.NumberFormat('pt-PT').format(num);
}

// utilitário para formatar tempo
function formatTime(ms) {
    return (ms / 1000).toFixed(1) + 's';
}

// utilitário para escapar HTML
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}

// validação de URLs antes de submeter
document.addEventListener('DOMContentLoaded', function() {
    const indexForms = document.querySelectorAll('form[action*="/index"]');
    
    indexForms.forEach(form => {
        form.addEventListener('submit', function(e) {
            const urlInput = form.querySelector('input[name="url"]');
            if (urlInput) {
                let url = urlInput.value.trim();
                
                // adiciona protocolo se não existir
                if (url && !url.match(/^https?:\/\//i)) {
                    url = 'http://' + url;
                    urlInput.value = url;
                }
                
                // validação básica de URL
                try {
                    new URL(url);
                } catch (err) {
                    e.preventDefault();
                    alert('Por favor, insere um URL válido');
                    return false;
                }
            }
        });
    });
    
    // auto-hide de alertas após 5 segundos
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.5s';
            alert.style.opacity = '0';
            setTimeout(() => alert.remove(), 500);
        }, 5000);
    });
});

// função global para toggle de elementos
window.toggleElement = function(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = element.style.display === 'none' ? 'block' : 'none';
    }
};
