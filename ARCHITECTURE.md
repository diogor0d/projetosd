# Arquitetura do Sistema Googol

## Visão Geral

```
┌─────────────┐
│   Cliente   │
│  (RMI)      │
└──────┬──────┘
       │
       │ RMI
       ▼
┌─────────────────────┐
│   RPC/RMI Gateway   │
│   - Load Balancing  │
│   - Caching         │
│   - URL Queue       │
└──────┬──────┬───────┘
       │      │
       │ RMI  │ RMI
       ▼      ▼
┌───────┐  ┌───────┐
│Barrel1│  │Barrel2│  (Réplicas)
│(Índice│  │(Índice│
│Invert)│  │Invert)│
└───┬───┘  └───┬───┘
    ▲          ▲
    │   RMI    │
    │ (Multicast)
    │          │
┌───┴──────────┴───┐
│   Downloaders    │
│  (Web Crawlers)  │
│   - Paralelos    │
│   - Usa jsoup    │
└──────────────────┘
```

## Componentes Detalhados

### 1. Storage Barrel (`barrel/StorageBarrel.java`)

**Responsabilidades:**

- Armazenar índice invertido (palavra → URLs)
- Armazenar metadados de páginas (PageInfo)
- Processar pesquisas
- Calcular relevância (baseado em inlinks)
- Fornecer estatísticas

**Estruturas de Dados:**

- `HashMap<String, Set<String>> invertedIndex` - Índice invertido
- `HashMap<String, PageInfo> pageDatabase` - Informação completa das páginas

**Tolerância a Falhas:**

- Múltiplas réplicas (Barrel1, Barrel2, ...)
- Recebem mesma informação dos Downloaders (multicast)
- Gateway detecta falhas e usa barrels disponíveis

### 2. Downloader (`downloader/Downloader.java`)

**Responsabilidades:**

- Obter páginas HTML usando jsoup
- Extrair informação (título, texto, palavras, links)
- Enviar PageInfo para todos os Barrels (multicast)
- Adicionar novos URLs à fila

**Funcionamento:**

- Thread que processa URLs da fila continuamente
- Mantém conjunto de URLs visitados (evita duplicação)
- Extrai links e adiciona à fila (crawling recursivo)

**Paralelização:**

- Múltiplos Downloaders trabalham simultaneamente
- Cada um processa URLs diferentes da fila partilhada

### 3. Gateway (`gateway/Gateway.java`)

**Responsabilidades:**

- Interface única para clientes
- Distribuir pesquisas entre Barrels (load balancing)
- Cache de resultados de pesquisa
- Gerir fila de URLs
- Agregar estatísticas do sistema

**Estratégias:**

- **Load Balancing:** Round-robin entre barrels ativos
- **Caching:** Armazena resultados de pesquisas recentes
- **Health Check:** Remove barrels inativos automaticamente

### 4. Cliente (`client/GoogolClient.java`)

**Funcionalidades:**

- Indexar URLs manualmente
- Pesquisar (com paginação)
- Ver páginas que apontam para URL
- Consultar estatísticas do sistema

### 5. URL Queue (`queue/URLQueue.java`)

**Características:**

- Fila thread-safe (BlockingQueue)
- Partilhada entre Downloaders
- URLs adicionados por: clientes, crawler recursivo

## Fluxos de Dados

### Fluxo de Indexação

```
Cliente → Gateway → URL Queue → Downloader → jsoup (HTTP) → Website
                                      ↓
                              PageInfo criado
                                      ↓
                              RMI Multicast
                                   ↙   ↘
                             Barrel1  Barrel2
                                   ↓
                          Índice atualizado
```

### Fluxo de Pesquisa

```
Cliente → Gateway → (verifica cache)
                         ↓ (cache miss)
                    Seleciona Barrel (round-robin)
                         ↓
                    Barrel.search()
                         ↓
                    1. Consulta índice invertido
                    2. Filtra por termos (interseção)
                    3. Ordena por relevância (inlinks)
                    4. Pagina resultados (10 por página)
                         ↓
                    Gateway ← Resultados
                         ↓
                    (armazena em cache)
                         ↓
                    Cliente ← Resultados
```

## Índice Invertido

### Estrutura

```
"universidade" → {"http://www.uc.pt", "https://wikipedia.org/wiki/University"}
"coimbra"      → {"http://www.uc.pt", "http://cm-coimbra.pt"}
"ciência"      → {"http://www.uc.pt"}
```

### PageInfo

```java
{
  url: "http://www.uc.pt",
  title: "Universidade de Coimbra",
  textSnippet: "A UC é uma das mais antigas...",
  words: {"universidade", "coimbra", "ciência", ...},
  inlinks: {"http://wikipedia.org/...", ...},  // backlinks
  outlinks: {"http://fctuc.pt", ...}
}
```

### Ordenação por Relevância

- Usa número de inlinks (backlinks) como métrica
- Páginas com mais referências aparecem primeiro
- Similar ao PageRank simplificado

## Comunicação RMI

### Interfaces Remotas

1. **IBarrel** - Exposta pelos Storage Barrels

   - `indexPage(PageInfo)` - Recebe dados do Downloader
   - `search(terms, page)` - Processa pesquisa
   - `getInlinks(url)` - Lista backlinks
   - `getStats()` - Estatísticas

2. **IGateway** - Exposta pela Gateway
   - `indexURL(url)` - Submete URL para indexação
   - `search(query, page)` - Realiza pesquisa
   - `getPagesPointingTo(url)` - Consulta backlinks
   - `getSystemStats()` - Estatísticas gerais

### RMI Registry

- Porta: 1099 (padrão)
- Nomes registados:
  - `rmi://localhost/Gateway`
  - `rmi://localhost/Barrel1`
  - `rmi://localhost/Barrel2`

## Considerações de Implementação

### Thread Safety

- **Barrels:** Usam ConcurrentHashMap para acesso concorrente
- **Gateway:** Cache thread-safe
- **URL Queue:** BlockingQueue (thread-safe por design)

### Tolerância a Falhas

- Gateway detecta barrels inativos (ping)
- Remove barrels falhos e usa alternativas
- Multicast garante dados em múltiplas réplicas

### Escalabilidade

- Downloaders horizontalmente escaláveis
- Barrels replicados (adicionar mais réplicas)
- Gateway única (pode ser replicada no futuro)

### Persistência

- **Atual:** Apenas em memória (dados perdem-se ao reiniciar)
- **Futuro:** Serialização para ficheiros ou base de dados

## Próximas Melhorias

### Funcionalidades Pendentes

1. **Stop Words (grupos de 3)** - Filtrar palavras comuns
2. **Persistência** - Guardar índice em disco
3. **Filtro de Bloom** - Otimizar verificação de URLs visitados
4. **Document IDs** - Reduzir repetição de URLs
5. **Ranking mais sofisticado** - TF-IDF, PageRank

### Otimizações

1. Compressão do índice
2. Índice particionado (sharding)
3. Cache distribuída
4. Processamento assíncrono melhorado

### Robustez

1. Tratamento de erros HTTP mais robusto
2. Retry automático em falhas de rede
3. Rate limiting para websites
4. Robots.txt compliance
