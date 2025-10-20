# Estrutura Final do Projeto Googol

## Estrutura de Diretórios

```
projetosd/
│
├── README.md                    # Visão geral do projeto
├── ARCHITECTURE.md              # Documentação da arquitetura
├── SETUP.md                     # Guia de instalação e execução
├── TODO.md                      # Próximos passos e tarefas
├── .gitignore                   # Ficheiros a ignorar no Git
│
├── src/                         # Código fonte
│   ├── common/                  # Classes e interfaces comuns
│   │   ├── IBarrel.java        # Interface RMI para Storage Barrel
│   │   ├── IGateway.java       # Interface RMI para Gateway
│   │   ├── PageInfo.java       # Informação sobre página indexada
│   │   ├── SearchResult.java   # Resultado de pesquisa
│   │   ├── BarrelStats.java    # Estatísticas de um barrel
│   │   └── SystemStats.java    # Estatísticas do sistema
│   │
│   ├── barrel/                  # Storage Barrels
│   │   └── StorageBarrel.java  # Servidor de índice invertido
│   │
│   ├── gateway/                 # RPC/RMI Gateway
│   │   └── Gateway.java        # Porta de entrada do sistema
│   │
│   ├── downloader/              # Web Crawlers
│   │   └── Downloader.java     # Crawler paralelo (usa jsoup)
│   │
│   ├── client/                  # Cliente
│   │   └── GoogolClient.java   # Interface de utilizador
│   │
│   └── queue/                   # Gestão de filas
│       └── URLQueue.java       # Fila de URLs para processar
│
├── lib/                         # Bibliotecas externas
│   └── README.md               # Instruções para download jsoup
│
├── config/                      # Ficheiros de configuração
│   └── system.properties       # Propriedades do sistema
│
├── data/                        # Dados persistentes (criado em runtime)
│   └── barrels/                # Dados dos barrels
│
├── scripts/                     # Scripts auxiliares
│   ├── start-barrel.sh         # Iniciar barrel (Linux/Mac)
│   └── start-barrel.bat        # Iniciar barrel (Windows)
│
└── bin/                         # Classes compiladas (criado em runtime)
```

## Componentes Principais

### 1. **Common** (Interfaces e Classes Partilhadas)

- **IBarrel**: Interface remota para Storage Barrels
- **IGateway**: Interface remota para Gateway
- **PageInfo**: Dados de uma página (URL, título, palavras, links)
- **SearchResult**: Resultado formatado de pesquisa
- **BarrelStats**: Estatísticas de um barrel
- **SystemStats**: Estatísticas agregadas do sistema

### 2. **Storage Barrel** (Armazenamento Distribuído)

- Índice invertido: `HashMap<String, Set<String>>`
- Base de dados de páginas: `HashMap<String, PageInfo>`
- Réplicas para tolerância a falhas
- Processa pesquisas e ordena por relevância

### 3. **Gateway** (Coordenação)

- Load balancing (round-robin)
- Cache de resultados
- Agregação de estatísticas
- Gestão de fila de URLs

### 4. **Downloader** (Web Crawler)

- Usa biblioteca jsoup
- Trabalha em paralelo
- Extrai palavras e links
- Envia para barrels via RMI multicast

### 5. **Cliente** (Interface)

- Menu interativo
- Indexação manual
- Pesquisa com paginação
- Consulta de backlinks
- Visualização de estatísticas

### 6. **URL Queue** (Fila de Trabalho)

- BlockingQueue thread-safe
- Partilhada entre downloaders
- Suporta crawling recursivo

## Fluxo de Comunicação

```
Cliente (RMI) → Gateway (RMI) → Barrels
                   ↓
              URL Queue
                   ↓
             Downloaders (RMI) → Barrels
```

## Tecnologias Utilizadas

- **Java RMI**: Comunicação distribuída
- **jsoup**: Parsing HTML e web scraping
- **HashMap/HashSet**: Estruturas de dados para índice
- **ConcurrentHashMap**: Thread safety
- **BlockingQueue**: Sincronização de threads

## Próximos Passos

1. ✅ Download do jsoup (ver `lib/README.md`)
2. ✅ Compilar projeto
3. ✅ Iniciar componentes (ver `SETUP.md`)
4. ✅ Testar funcionalidades básicas
5. ⏳ Implementar melhorias (ver `TODO.md`)

## Documentação Adicional

- **ARCHITECTURE.md**: Arquitetura detalhada e diagramas
- **SETUP.md**: Guia completo de instalação e execução
- **TODO.md**: Lista de tarefas e funcionalidades pendentes
- **lib/README.md**: Como obter biblioteca jsoup

## Requisitos do Enunciado

✅ Indexação manual de URLs  
✅ Indexação recursiva/iterativa  
✅ Pesquisa por termos  
✅ Ordenação por relevância (backlinks)  
✅ Consulta de páginas que apontam para URL  
✅ Estatísticas em tempo real  
⏳ Stop words distribuídas (grupos de 3)

✅ Downloaders paralelos  
✅ Storage Barrels replicados  
✅ RPC/RMI Gateway  
✅ Cliente RMI  
✅ URL Queue  
✅ Índice invertido  
⏳ Multicast confiável sobre RMI
