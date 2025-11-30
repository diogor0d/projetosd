# Googol Web Application - Meta 2

## Visão Geral

Interface web para o motor de pesquisa Googol desenvolvido na Meta 1. Esta aplicação Spring Boot fornece acesso via browser às funcionalidades do sistema distribuído, incluindo pesquisa, indexação e monitorização em tempo real.

## Arquitetura

- **Backend**: Spring Boot 3.2.0 com arquitetura MVC
- **Frontend**: Thymeleaf + HTML/CSS/JavaScript
- **Comunicação RMI**: Cliente do Gateway desenvolvido na Meta 1
- **WebSockets**: Atualizações em tempo real usando STOMP
- **APIs REST**: Integração com Hacker News e OpenAI

## Estrutura do Projeto

```
src/frontend/
├── WebApplication.java          # classe principal Spring Boot
├── config/
│   └── WebSocketConfig.java     # configuração WebSocket/STOMP
├── controllers/
│   ├── SearchController.java    # controlador MVC principal
│   └── ApiController.java       # endpoints REST API
└── services/
    ├── GatewayService.java      # cliente RMI do Gateway
    ├── StatsUpdateService.java  # serviço push de estatísticas
    ├── HackerNewsService.java   # integração Hacker News API
    └── OpenAIService.java       # integração OpenAI API

src/main/resources/
├── templates/                   # views Thymeleaf
│   ├── index.html              # página inicial
│   ├── search.html             # resultados de pesquisa
│   ├── stats.html              # estatísticas do sistema
│   └── links.html              # páginas que apontam para URL
├── static/
│   ├── css/style.css           # estilos principais
│   └── js/
│       ├── app.js              # JavaScript geral
│       └── stats-websocket.js  # cliente WebSocket
└── application.properties       # configuração Spring Boot
```

## Funcionalidades Implementadas

### 1. Indexação de URLs

- Formulário para submeter URLs manualmente
- Validação e normalização de URLs
- Feedback visual de sucesso/erro

### 2. Pesquisa de Páginas

- Interface de pesquisa semelhante ao Google
- Resultados paginados (10 por página)
- Apresentação de título, URL e citação
- Navegação entre páginas de resultados

### 3. Páginas que Apontam para URL

- Consulta de backlinks para qualquer URL indexado
- Acesso direto a partir dos resultados de pesquisa

### 4. Estatísticas em Tempo Real

- Dashboard com métricas do sistema
- Top 10 pesquisas mais comuns
- Barrels ativos com tamanho do índice
- Tempo médio de resposta por barrel
- Atualização via WebSocket (push server-side)
- Indicador visual de conexão ativa

### 5. Integração Hacker News

- Busca de top stories relacionadas com termos de pesquisa
- Indexação automática de URLs do HN
- Apresentação de stories relacionadas nos resultados

### 6. Análise com IA (OpenRouter.ai)

- Geração de análises contextualizadas dos resultados
- Usa API OpenRouter.ai (compatível com OpenAI)
- Suporta múltiplos modelos LLM (grátis e pagos)
- Modelos grátis disponíveis: Llama 3.1, Gemini Flash
- Identificação de padrões e insights
- Apresentação visual destacada

### 7. API REST

- Endpoints programáticos para integração
- `/api/search` - pesquisa
- `/api/index` - indexação
- `/api/stats` - estatísticas
- `/api/links-to` - backlinks
- `/api/health` - health check

## Configuração

### Pré-requisitos

- Java 21
- Maven 3.8+
- Gateway RMI em execução (Meta 1)
- Pelo menos um Storage Barrel ativo

### Configuração RMI

Edita `src/main/resources/application.properties`:

```properties
googol.rmi.host=localhost
googol.rmi.port=7000
googol.rmi.name=Gateway
```

### Configuração OpenRouter.ai (opcional)

**Obter chave API:**

1. Criar conta em https://openrouter.ai
2. Ir para https://openrouter.ai/keys
3. Criar nova chave API
4. Copiar chave (formato: `sk-or-v1-...`)

**Configurar no projeto:**

Opção 1 - Ficheiro (recomendado):

```bash
echo "sk-or-v1-xxxxxxxxxx" > key.openrouterai.txt
```

Opção 2 - application.properties:

```properties
openrouter.api.key=sk-or-v1-xxxxxxxxxx
```

**Escolher modelo LLM:**

Modelos grátis (não requerem pagamento):

```properties
# Llama 3.1 8B (rápido, boa qualidade)
openrouter.model=meta-llama/llama-3.1-8b-instruct:free

# Google Gemini Flash 1.5
openrouter.model=google/gemini-flash-1.5
```

Modelos pagos (melhor qualidade):

```properties
# Claude 3.5 Sonnet (excelente para análises)
openrouter.model=anthropic/claude-3.5-sonnet

# GPT-4o (OpenAI)
openrouter.model=openai/gpt-4o

# Llama 3.1 70B (bom custo-benefício)
openrouter.model=meta-llama/llama-3.1-70b-instruct
```

Ver todos os modelos disponíveis em: https://openrouter.ai/models

## Execução

### Opção 1: Maven (recomendado)

```bash
mvn spring-boot:run
```

### Opção 2: VS Code Task

Usa a task "Run Web Application" no VS Code

### Opção 3: JAR compilado

```bash
mvn clean package
java -jar target/googol-1.0-SNAPSHOT.jar
```

A aplicação estará disponível em: **http://localhost:8080**

## Fluxo de Utilização

1. **Iniciar sistema backend** (Meta 1):

   - Gateway: `mvn exec:java@gateway`
   - Barrel(s): `mvn exec:java@barrel -Dexec.arguments=1`
   - Downloader(s): `mvn exec:java@downloader -Dexec.arguments=1`

2. **Iniciar aplicação web**:

   - `mvn spring-boot:run`

3. **Aceder via browser**:

   - Página inicial: http://localhost:8080
   - Estatísticas: http://localhost:8080/stats
   - API: http://localhost:8080/api/health

4. **Indexar URLs**:

   - Usar formulário na página inicial
   - URLs do Hacker News a partir dos resultados de pesquisa

5. **Pesquisar**:

   - Inserir termos na caixa de pesquisa
   - Opcionalmente ativar análise IA
   - Explorar resultados e backlinks

6. **Monitorizar**:
   - Aceder à página de estatísticas
   - Observar atualizações em tempo real via WebSocket

## WebSocket - Atualizações em Tempo Real

A aplicação usa STOMP sobre WebSocket para push de estatísticas:

- **Endpoint**: `/ws-stats`
- **Tópico**: `/topic/stats`
- **Fallback**: SockJS para compatibilidade
- **Reconexão**: Automática a cada 5s se desconectar

As estatísticas são enviadas pelo `StatsUpdateService` sempre que o Gateway deteta alterações, garantindo atualizações instantâneas sem polling.

## Tecnologias Utilizadas

### Backend

- Spring Boot 3.2.0
- Spring Web MVC
- Spring WebSocket
- Thymeleaf Template Engine
- Java RMI (cliente)

### Frontend

- HTML5 + CSS3
- JavaScript ES6+
- STOMP.js + SockJS (WebSocket)
- Design responsivo

### APIs Externas

- Hacker News Firebase API (REST)
- OpenRouter.ai LLM API (REST - compatível com OpenAI)

### Dependências Principais

- `spring-boot-starter-web` - framework web
- `spring-boot-starter-thymeleaf` - templates
- `spring-boot-starter-websocket` - comunicação real-time
- `gson` - serialização JSON
- `jsoup` - parsing HTML (reutilizado da Meta 1)

## Estrutura MVC

**Model**: Classes do pacote `common` (Meta 1)

- `SearchResult`
- `SystemStats`
- `BarrelStats`

**View**: Templates Thymeleaf

- `index.html`
- `search.html`
- `stats.html`
- `links.html`

**Controller**:

- `SearchController` - lógica de negócio web
- `ApiController` - endpoints REST

**Services**: Camada de serviço

- `GatewayService` - abstração RMI
- `HackerNewsService` - integração externa
- `OpenAIService` - IA generativa
- `StatsUpdateService` - push WebSocket

## Notas de Desenvolvimento

- A aplicação mantém compatibilidade total com o cliente desktop da Meta 1
- O Gateway RMI **deve estar em execução** antes de iniciar a web app
- WebSockets requerem browser moderno (Chrome, Firefox, Safari, Edge)
- A análise IA é opcional e requer configuração de chave API
- Todos os endpoints são acessíveis via REST API para integração
- Hot reload ativo durante desenvolvimento (Spring DevTools)

## Troubleshooting

**Erro: Connection refused ao Gateway**

- Verifica se o Gateway RMI está em execução
- Confirma host/port em `application.properties`

**WebSocket não conecta**

- Verifica console do browser para erros
- SockJS fornece fallback automático
- Polling a cada 5s garante atualizações mesmo sem WS

**Análise IA não funciona**

- Verifica chave OpenRouter em `key.openrouterai.txt` ou `application.properties`
- Confirma que tens créditos/quota disponível em https://openrouter.ai/credits
- Modelos grátis têm rate limits, experimenta outro modelo
- Análise é opcional, aplicação funciona sem ela
- Verifica logs: `[OpenAIService] Erro ao gerar análise:`

**Estilos não carregam**

- Limpa cache do browser
- Verifica se ficheiros estão em `src/main/resources/static/`
- Spring DevTools pode requerer rebuild

## Autores

Projeto desenvolvido para a cadeira de Sistemas Distribuídos - LEI UC 2024/2025
