# TODO - Próximos Passos do Projeto

## ✅ Completado

- [x] Estrutura básica do projeto
- [x] Classes comuns (PageInfo, SearchResult, etc.)
- [x] Interfaces RMI (IBarrel, IGateway)
- [x] Storage Barrel (indexação e pesquisa)
- [x] Gateway (load balancing, cache)
- [x] Cliente RMI com menu interativo
- [x] Downloader básico (jsoup)
- [x] URL Queue
- [x] Documentação inicial

## 🔨 Em Desenvolvimento

### 1. Testar e Corrigir Sistema Básico

- [ ] Baixar biblioteca jsoup
- [ ] Compilar todos os componentes
- [ ] Testar inicialização de componentes
- [ ] Testar indexação manual de URL
- [ ] Testar pesquisa básica
- [ ] Corrigir bugs encontrados

### 2. Melhorar Downloader

- [ ] Integrar com URLQueue da Gateway
- [ ] Implementar coordenação entre múltiplos downloaders
- [ ] Adicionar validação robusta de URLs
- [ ] Tratar exceções de rede adequadamente
- [ ] Implementar rate limiting
- [ ] Respeitar robots.txt

### 3. Implementar Multicast Confiável

- [ ] Protocolo de multicast sobre RMI para 2 barrels
- [ ] Garantir que ambos os barrels recebem mesmos dados
- [ ] Implementar ACKs e retransmissões
- [ ] Testar cenário de falha de barrel

### 4. Persistência de Dados

- [ ] Serializar índice invertido para ficheiro
- [ ] Carregar índice na inicialização
- [ ] Implementar checkpoints periódicos
- [ ] Opção: usar base de dados (H2, SQLite)

### 5. Melhorias de Pesquisa

- [ ] Implementar ranking TF-IDF
- [ ] Otimizar pesquisas com múltiplos termos
- [ ] Adicionar pesquisa por frase exata
- [ ] Suporte para operadores (AND, OR, NOT)
- [ ] Highlighting de termos no snippet

### 6. Estatísticas em Tempo Real

- [ ] Implementar callbacks/observers para atualizações
- [ ] Tracking de pesquisas mais comuns
- [ ] Monitoramento de tempo de resposta por barrel
- [ ] Dashboard com métricas em tempo real

### 7. Filtros e Otimizações

- [ ] Implementar Filtro de Bloom para URLs visitados
- [ ] Usar Document IDs em vez de URLs completos
- [ ] Compressão de dados transmitidos
- [ ] Cache distribuída

## 🎓 Funcionalidades Avançadas (Grupos de 3)

### Stop Words (Aprendizagem Distribuída)

- [ ] Contar frequência de palavras durante indexação
- [ ] Partilhar estatísticas entre barrels
- [ ] Identificar palavras muito comuns (>X%)
- [ ] Remover do índice dinamicamente
- [ ] Configurar threshold ajustável

## 🧪 Testes

### Testes Unitários

- [ ] Testar PageInfo
- [ ] Testar índice invertido
- [ ] Testar ordenação por relevância
- [ ] Testar paginação

### Testes de Integração

- [ ] Testar comunicação RMI
- [ ] Testar multicast para barrels
- [ ] Testar failover de barrels
- [ ] Testar múltiplos downloaders simultâneos

### Testes de Carga

- [ ] Indexar 100+ páginas
- [ ] Pesquisas simultâneas de múltiplos clientes
- [ ] Medir tempos de resposta
- [ ] Verificar consumo de memória

## 📝 Documentação

- [ ] Javadoc para todas as classes públicas
- [ ] Diagramas UML (classes, sequência)
- [ ] Manual de utilizador
- [ ] Relatório técnico do projeto
- [ ] Vídeo de demonstração

## 🐛 Bugs Conhecidos

1. **jsoup não importado** - Adicionar JAR à pasta lib/
2. **queryCount não usado** - Usar ou remover variável
3. **MAX_DEPTH não usado** - Implementar limite de profundidade
4. **URL constructor deprecated** - Usar URI.toURL() alternativo

## 💡 Ideias Futuras

- Interface web (servlet/JSP ou REST API)
- Suporte para múltiplos idiomas
- Indexação de PDFs
- Pesquisa de imagens
- Autocomplete de pesquisas
- Histórico de pesquisas por utilizador
- Bookmarks/favoritos
- Exportar resultados (CSV, JSON)

## 📚 Recursos Necessários

### Bibliotecas

- [x] jsoup (HTML parsing) - https://jsoup.org/
- [ ] Gson/Jackson (JSON, opcional)
- [ ] JUnit (testes, opcional)

### Ferramentas

- Java JDK 11+
- IDE (Eclipse, IntelliJ, VS Code)
- Git (controlo de versão)

## 🎯 Prioridades Imediatas

1. **ALTA** - Baixar e configurar jsoup
2. **ALTA** - Testar sistema básico end-to-end
3. **ALTA** - Implementar multicast confiável
4. **MÉDIA** - Adicionar persistência
5. **MÉDIA** - Melhorar coordenação de downloaders
6. **BAIXA** - Interface gráfica

## ⏱️ Timeline Sugerida

### Semana 1-2

- Configurar ambiente e dependências
- Testar e corrigir sistema básico
- Implementar multicast confiável

### Semana 3-4

- Adicionar persistência
- Melhorar downloaders
- Implementar estatísticas em tempo real

### Semana 5-6

- Testes extensivos
- Otimizações de performance
- Documentação completa

### Semana 7-8

- Funcionalidades avançadas (stop words)
- Refinamentos finais
- Preparar apresentação
