(function() {
    const getStoredTheme = () => localStorage.getItem('theme') || 'dark';
    const setStoredTheme = (theme) => localStorage.setItem('theme', theme);

    const applyTheme = (theme) => {
        document.documentElement.setAttribute('data-theme', theme);
    };

    // aplica o tema antes da pagina carregar (para não ser flashado)
    const theme = getStoredTheme();
    applyTheme(theme);

    const toggleTheme = () => {
        const currentTheme = getStoredTheme();
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        setStoredTheme(newTheme);
        applyTheme(newTheme);
    };

    // inicializa o botão do toggle quando o DOM estiver pronto
    const initToggleButton = () => {
        const themeToggle = document.getElementById('theme-toggle');
        if (themeToggle) {
            themeToggle.addEventListener('click', toggleTheme);
        }
    };

    // espera o DOM estar pronto
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initToggleButton);
    } else {
        initToggleButton();
    }
})();

