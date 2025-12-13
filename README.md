# Googol - Motor de Pesquisa Distribuído

## Descrição do Projeto

Sistema completo de pesquisa web distribuído com funcionalidades semelhantes ao Google. Implementa indexação automática através de web crawlers paralelos e motor de busca com índice invertido. Inclui interface web interativa para acesso via browser, cliente de linha de comandos, e integrações com APIs externas (Hacker News e OpenRouter.ai para análises com IA).

## Arquitetura do Sistema

O sistema é dividido em duas metas principais:

### Meta 1 - Backend Distribuído (RMI)

Componentes de processamento distribuído que implementam o motor de pesquisa:

1. **Downloaders** - Crawlers paralelos que obtêm e processam páginas web

   - Comando: mvn compile exec:java@downloader -Dexec.arguments=<id>
   - Função: Fazer download de páginas, processar HTML, extrair texto e ligações
   - Escalável: Múltiplas instâncias podem executar em paralelo

2. **Index Storage Barrels** - Servidores replicados que armazenam o índice invertido

   - Comando: mvn compile exec:java@barrel -Dexec.arguments=<id>
   - Função: Armazenar índice invertido distribuído, responder a pesquisas
   - Escalável: Replicação de dados para redundância e melhor desempenho

3. **RMI Gateway** - Porta de entrada centralizada para clientes

   - Comando: mvn compile exec:java@gateway
   - Função: Orquestrar operações, distribuir pesquisas, gerir fila de URLs
   - Responsabilidades: Balanceamento, coordenação, cache de resultados

4. **Cliente de Linha de Comandos (RMI Client)** - Interface de texto para testes
   - Comando: mvn compile exec:java@client
   - Função: Permitir operações de pesquisa e indexação via terminal

### Meta 2 - Frontend Web (Spring Boot)

5. **Aplicação Web** - Interface gráfica web com Spring Boot, Thymeleaf e WebSockets
   - Comando: mvn spring-boot:run
   - Porta: 8081 (HTTPS)
   - Funcionalidades:
     - Página inicial com caixa de pesquisa integrada
     - Página de resultados com paginação estilo Google
     - Página de estatísticas com atualizações em tempo real via WebSocket
     - Página de backlinks (páginas que apontam para um URL)
     - Dashboard de indexação manual de URLs
     - Integração com Hacker News para indexar stories
     - Análise automática de resultados com IA (OpenRouter.ai)
     - Verificação ortográfica automática com IA
     - REST API para integração programática
     - Temas claro e escuro
     - Interface responsiva para mobile e desktop

## Estrutura do Projeto

```
projetosd/
├── src/
│   ├── common/
│   │   ├── IBarrel.java              # Interface RMI para barrels
│   │   ├── IGateway.java             # Interface RMI para gateway
│   │   ├── IBarrelRegistry.java       # Interface de registo de barrels
│   │   ├── IStatsListener.java        # Interface para listeners de estatísticas
│   │   ├── PageInfo.java              # Informações de página indexada
│   │   ├── SearchResult.java          # Resultado de pesquisa
│   │   ├── SearchResponse.java        # Resposta com resultados paginados
│   │   ├── SystemStats.java           # Estatísticas do sistema
│   │   ├── BarrelStats.java           # Estatísticas por barrel
│   │   ├── TextUtils.java             # Utilitários de processamento de texto
│   │   └── RMIConfig.java             # Configurações RMI
│   │
│   ├── downloader/
│   │   └── Downloader.java            # Web crawler paralelo
│   │
│   ├── barrel/
│   │   └── StorageBarrel.java         # Servidor de armazenamento do índice
│   │
│   ├── gateway/
│   │   ├── Gateway.java               # Servidor Gateway RMI
│   │   ├── BarrelRegistry.java         # Registo dinâmico de barrels
│   │   └── URLQueue.java              # Fila de URLs para processar
│   │
│   ├── client/
│   │   └── GoogolClient.java           # Cliente RMI de linha de comandos
│   │
│   ├── queue/
│   │   └── (Estruturas de gestão de filas)
│   │
│   └── frontend/
│       ├── java/
│       │   ├── GoogolApplication.java          # Aplicação principal Spring Boot
│       │   ├── config/
│       │   │   └── WebSocketConfig.java         # Configuração STOMP/WebSocket
│       │   ├── controllers/
│       │   │   ├── SearchController.java        # Controlador MVC (páginas HTML)
│       │   │   └── ApiController.java           # Controlador REST (endpoints JSON)
│       │   └── services/
│       │       ├── GatewayService.java          # Cliente RMI para comunicação com Gateway
│       │       ├── StatsUpdateService.java      # Listener RMI e bridge WebSocket
│       │       ├── HackerNewsService.java       # Cliente REST para Hacker News API
│       │       ├── OpenAIService.java           # Cliente REST para OpenRouter.ai API
│       │       └── SpellCheckService.java       # Verificação ortográfica com IA
│       │
│       └── webapp/
│           ├── keystore.p12                     # Certificado SSL/TLS para HTTPS
│           ├── static/
│           │   ├── css/
│           │   │   └── style.css                # Estilos CSS (design system, temas)
│           │   ├── js/
│           │   │   ├── app.js                   # Funcionalidades JavaScript gerais
│           │   │   ├── stats-websocket.js       # Cliente STOMP para stats em tempo real
│           │   │   ├── global-stats.js          # Renderização de estatísticas
│           │   │   ├── mode-switch.js           # Switcher de modos (Search/Index/HN)
│           │   │   └── theme-toggle.js          # Toggle tema dark/light
│           │   ├── googol_logo.svg              # Logo da aplicação
│           │   └── googol_favicon.svg            # Favicon
│           └── templates/
│               ├── fragments/
│               │   └── layout.html              # Layout base reutilizável
│               ├── index.html                   # Página inicial
│               ├── search.html                  # Página de resultados
│               ├── links.html                   # Página de backlinks
│               └── stats.html                   # Dashboard de estatísticas
│
├── config/
│   ├── application.properties                   # Configurações Spring Boot
│   ├── key.openrouterai.txt                     # Chave API OpenRouter.ai
│   └── system.properties                        # Propriedades do sistema
│
├── data/
│   ├── barrel_1_index.json                      # Índice persistente do barrel 1
│   ├── index10kpaginas.json                     # Dataset de teste (10k páginas)
│   ├── stopwords-en.txt                         # Palavras comuns em inglês
│   └── stopwords-pt.txt                         # Palavras comuns em português
│
├── scripts/
│   ├── start-system.sh                          # Script para iniciar sistema (Linux/Mac)
│   ├── start-system.bat                         # Script para iniciar sistema (Windows)
│   ├── stop-system.sh                           # Script para parar sistema
│   └── api-examples.sh                          # Exemplos de chamadas à API REST
│
├── postman/
│   ├── Googol-API.postman_collection.json       # Coleção Postman com endpoints
│   ├── Googol-API.postman_environment.json      # Configurações de ambiente
│   └── README.md                                # Documentação de utilização
│
├── lib/                                         # Bibliotecas externas
├── ssl-certificate/                             # Certificados SSL
├── docs/                                        # Documentação adicional
├── pom.xml                                      # Configuração Maven
└── projetosd.code-workspace                     # Configuração VS Code
```

## Tecnologias Utilizadas

### Backend Distribuído (Meta 1)

- Java 21
- RMI (Remote Method Invocation) para comunicação distribuída
- Bloom Filters para otimização de pesquisa
- Processamento paralelo com threads
- Serialização e persistência de dados

### Frontend Web (Meta 2)

- Spring Boot 3.2.0
- Spring MVC para controladores web
- Thymeleaf para template rendering
- WebSocket com STOMP para comunicação em tempo real
- JavaScript vanilla com fetch API
- CSS3 com variáveis e design responsivo
- HTTPS/TLS com certificados automáticos

### Integrações Externas

- Hacker News API (REST)
- OpenRouter.ai API (modelos LLM: Llama 3.1, Gemini Flash)
- jsoup para parsing HTML
- Gson para serialização JSON

## Instruções de Execução

### Pré-requisitos

- Java 21 ou superior
- Maven 3.6+
- Conexão à internet (para APIs externas)

### Compilação

```
mvn clean compile
```

### Meta 1 - Sistema Distribuído RMI

1. Iniciar o Gateway:

```
mvn compile exec:java@gateway
```

2. Iniciar Barrels (em terminais separados):

```
mvn compile exec:java@barrel -Dexec.arguments=1
mvn compile exec:java@barrel -Dexec.arguments=2
```

3. Iniciar Downloaders (em terminais separados):

```
mvn compile exec:java@downloader -Dexec.arguments=1
mvn compile exec:java@downloader -Dexec.arguments=2
```

4. Utilizar Cliente de Linha de Comandos:

```
mvn compile exec:java@client
```

### Meta 2 - Aplicação Web

Iniciar o servidor web (porta 8081):

```
mvn spring-boot:run
```

Ou utilizando a tarefa VS Code "Run Web Application"

Aceder via navegador: https://localhost:8081

### Scripts de Início Automático

Para Linux/Mac:

```
./scripts/start-system.sh
```

Para Windows:

```
scripts\start-system.bat
```

Para parar o sistema:

```
./scripts/stop-system.sh
```

## Configuração

As configurações principais estão em `config/application.properties`:

- `server.port` - Porta do servidor web (padrão: 8081)
- `googol.rmi.host` - Host RMI (padrão: localhost)
- `googol.rmi.port` - Porta RMI (padrão: 1099)
- `googol.rmi.name` - Nome RMI (padrão: Gateway)
- `openrouter.model` - Modelo IA a utilizar (padrão: meta-llama/llama-3.3-70b-instruct:free)
- `openrouter.api.key` - Chave API OpenRouter (opcional, pode estar em variável de ambiente)

## REST API

A aplicação web expõe os seguintes endpoints REST:

### Pesquisa

```
GET /api/search?q=termo&page=1
```

Resposta: Lista de resultados com informações de página

### Indexação

```
POST /api/index
Body: {"url": "https://exemplo.com"}
```

Resposta: Confirmação de submissão para indexação

### Estatísticas

```
GET /api/stats
```

Resposta: Estatísticas do sistema (páginas indexadas, URLs em fila, estado dos barrels)

### Sugestão Ortográfica

```
GET /api/spellcheck?q=termo
```

Resposta: Sugestão de correção (ou null se correto)

Consultar `postman/` para exemplos completos

## Funcionalidades

### Pesquisa

- Pesquisa por palavras-chave em tempo real
- Paginação de resultados
- Destaque de termos nos trechos
- Tempo de resposta em milissegundos

### Indexação

- Indexação manual de URLs via interface web
- Indexação automática de stories do Hacker News
- Submissão de URLs via REST API
- Fila de processamento assíncrono

### Estatísticas em Tempo Real

- Número total de páginas indexadas
- URLs aguardando indexação
- Estado de cada barrel distribuído
- Top 10 pesquisas mais comuns
- Atualização automática via WebSocket

### IA e Análise

- Geração automática de análises contextualizadas dos resultados
- Verificação ortográfica com sugestões de correção
- Modelos LLM gratuitos (Llama 3.1, Gemini Flash) via OpenRouter.ai

### Interface

- Design responsivo (mobile e desktop)
- Temas claro e escuro
- Navegação intuitiva
- Indicador de conexão WebSocket em tempo real

## Tratamento de Erros

A aplicação implementa:

- Retry automático para conexões RMI perdidas
- Reconexão automática de WebSocket
- Fallback para APIs externas indisponíveis
- Tratamento gracioso de URLs inválidos
- Limites de requisição e timeout

## Notas de Desenvolvimento

- Código-fonte: `src/`
- Compilados: `target/`
- Logs: Console (configurável em `application.properties`)
- Cache: Em memória (sem persistência)
- Armazenamento de índices: Ficheiros JSON em `data/`

## Autores

Projeto de Sistemas Distribuídos - Universidade de Coimbra
