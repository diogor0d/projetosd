# 🔧 Como Funciona o Frontend e a Integração com o Sistema

## 📐 Arquitetura Completa

```
┌─────────────────────────────────────────────────────────────────┐
│                        UTILIZADOR (Browser)                      │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP/WebSocket
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              SPRING BOOT WEB APPLICATION (Meta 2)                │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │ Controllers  │  │  Services    │  │  WebSocket Config  │    │
│  │   (MVC)      │──│  (Business)  │  │    (Real-time)     │    │
│  └──────────────┘  └──────┬───────┘  └────────────────────┘    │
│                           │                                      │
│                           │ RMI Lookup                           │
└───────────────────────────┼──────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RMI GATEWAY (Meta 1)                          │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │  URL Queue     │  │  Barrel        │  │   Stats Push     │  │
│  │   Manager      │  │  Load Balance  │  │   (Listeners)    │  │
│  └────────────────┘  └────────┬───────┘  └──────────────────┘  │
└───────────────────────────────┼──────────────────────────────────┘
                                │ Multicast Discovery
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STORAGE BARRELS (Meta 1)                      │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                │
│  │  Barrel 1  │  │  Barrel 2  │  │  Barrel N  │                │
│  │  (Índice)  │  │  (Índice)  │  │  (Índice)  │                │
│  └────────────┘  └────────────┘  └────────────┘                │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ URLs indexados
                                │
┌─────────────────────────────────────────────────────────────────┐
│                    DOWNLOADERS (Meta 1)                          │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │ Downloader 1 │  │ Downloader 2 │  (Web Crawlers)             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     APIS EXTERNAS (Meta 2)                       │
│  ┌──────────────────┐  ┌──────────────────────────────────────┐ │
│  │  Hacker News API │  │  OpenRouter.ai (LLM)                 │ │
│  │  (Firebase REST) │  │  (Análise IA dos resultados)         │ │
│  └──────────────────┘  └──────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Fluxo de Dados Detalhado

### 1️⃣ **Pesquisa de Páginas**

```
[Browser]
  │ HTTP GET /search?q=java&page=1
  ▼
[SearchController]
  │ @GetMapping("/search")
  │ recebe parâmetros: query, page, aiAnalysis
  ▼
[GatewayService]
  │ search(query, page)
  │ RMI call via rede
  ▼
[Gateway RMI] - localhost:7000
  │ distribui pesquisa entre barrels ativos
  │ usa load balancing baseado em tempo de resposta
  ▼
[Storage Barrels]
  │ consulta índice invertido local
  │ calcula relevância (TF-IDF + inlinks)
  │ retorna lista de SearchResult
  ▼
[Gateway RMI]
  │ agrega resultados de todos os barrels
  │ ordena por relevância
  │ aplica paginação (10 resultados)
  │ atualiza cache de pesquisas comuns
  ▼
[SearchController]
  │ adiciona resultados ao Model
  │ opcionalmente chama OpenRouter.ai para análise
  │ busca stories do Hacker News
  ▼
[Thymeleaf Engine]
  │ renderiza search.html com dados
  ▼
[Browser]
  │ apresenta HTML com resultados
```

**Código envolvido:**

- `SearchController.search()` - recebe pedido HTTP
- `GatewayService.search()` - wrapper RMI
- `Gateway.search()` - distribui entre barrels (Meta 1)
- `StorageBarrel.search()` - consulta índice (Meta 1)

---

### 2️⃣ **Indexação de URL**

```
[Browser]
  │ HTTP POST /index
  │ form data: url=https://example.com
  ▼
[SearchController]
  │ @PostMapping("/index")
  │ valida e normaliza URL
  ▼
[GatewayService]
  │ indexURL(url)
  │ RMI call
  ▼
[Gateway RMI]
  │ addDiscoveredURL(url, highPriority=true)
  │ marca como não visitado (Bloom filter)
  │ adiciona à URLQueue (prioridade alta)
  ▼
[URLQueue]
  │ armazena em PriorityBlockingQueue
  │ URLs do utilizador têm prioridade sobre crawled
  ▼
[Downloaders]
  │ pollURL() - bloqueante, espera por URLs
  │ faz HTTP GET do URL
  │ extrai links, título, texto
  │ envia para barrel via RMI
  ▼
[Storage Barrel]
  │ atualiza índice invertido
  │ persiste em disco (JSON)
  │ notifica Gateway de alteração
  ▼
[Browser]
  │ mensagem flash: "URL submetido para indexação"
```

**Código envolvido:**

- `SearchController.indexURL()` - validação
- `GatewayService.indexURL()` - RMI call
- `URLQueue.addURL()` - gestão de fila (Meta 1)
- `Downloader.run()` - crawler loop (Meta 1)

---

### 3️⃣ **Estatísticas em Tempo Real (WebSocket)**

```
[Browser]
  │ WebSocket connect /ws-stats
  │ SockJS + STOMP protocol
  ▼
[WebSocketConfig]
  │ configura broker: /topic/stats
  │ aceita conexões
  ▼
[StatsUpdateService]
  │ @PostConstruct: conecta ao Gateway RMI
  │ registerStatsListener(this)
  │ implementa IStatsListener (RMI Remote)
  ▼
[Gateway RMI]
  │ detecta mudanças no sistema:
  │   - nova pesquisa → atualiza top searches
  │   - barrel ativo/inativo → atualiza lista
  │   - tempo de resposta → recalcula média
  │ invoca via RMI: listener.updateStats(newStats)
  ▼
[StatsUpdateService]
  │ @Override updateStats(SystemStats stats)
  │ recebe push do Gateway
  ▼
[SimpMessagingTemplate]
  │ messagingTemplate.convertAndSend("/topic/stats", stats)
  │ serializa SystemStats para JSON
  ▼
[WebSocket Broker]
  │ envia para todos os clientes subscritos
  ▼
[Browser JavaScript]
  │ stats-websocket.js
  │ stompClient.subscribe('/topic/stats', callback)
  │ recebe JSON com novas estatísticas
  │ atualiza DOM dinamicamente (sem reload)
  ▼
[UI atualizada]
  │ números animados, indicador verde pulsante
```

**Código envolvido:**

- `WebSocketConfig` - configuração STOMP
- `StatsUpdateService` - listener RMI que faz bridge para WebSocket
- `stats-websocket.js` - cliente JavaScript
- `Gateway.notifyStatsListeners()` - push RMI (Meta 1)

**Fallback:** polling a cada 5s caso WebSocket falhe

---

### 4️⃣ **Integração Hacker News**

```
[SearchController]
  │ após pesquisa, chama HackerNewsService
  ▼
[HackerNewsService]
  │ getTopStoriesWithTerms(query, maxStories)
  │ HTTP GET https://hacker-news.firebaseio.com/v0/topstories.json
  ▼
[Hacker News API]
  │ retorna array de IDs: [123456, 123457, ...]
  ▼
[HackerNewsService]
  │ para cada ID:
  │   HTTP GET /v0/item/{id}.json
  │   verifica se título contém termos de pesquisa
  │   constrói HNStory(title, url, score, by, time)
  ▼
[SearchController]
  │ adiciona lista de stories ao Model
  ▼
[search.html]
  │ apresenta stories relacionadas em secção separada
  │ botão para indexar URLs do HN
  ▼
[POST /index-hackernews]
  │ itera sobre stories e chama indexURL() para cada uma
```

**Código envolvido:**

- `HackerNewsService.getTopStoriesWithTerms()` - filtra stories
- `SearchController.indexHackerNews()` - submete URLs

---

### 5️⃣ **Análise IA com OpenRouter.ai**

```
[Browser]
  │ clica botão "🤖 Análise IA"
  │ HTTP GET /search?q=java&aiAnalysis=true
  ▼
[SearchController]
  │ if (aiAnalysis && openAIService.isAvailable())
  ▼
[OpenAIService]
  │ generateAnalysis(query, results)
  │ constrói prompt com top 5 resultados
  │ cria JSON request body
  ▼
[RestTemplate]
  │ HTTP POST https://openrouter.ai/api/v1/chat/completions
  │ Headers:
  │   - Authorization: Bearer sk-or-v1-xxx
  │   - HTTP-Referer: http://localhost:8080
  │   - X-Title: Googol Search Engine
  │ Body:
  │   {
  │     "model": "meta-llama/llama-3.1-8b-instruct:free",
  │     "messages": [{"role": "user", "content": "..."}],
  │     "max_tokens": 500,
  │     "temperature": 0.7
  │   }
  ▼
[OpenRouter.ai API]
  │ roteia para modelo LLM configurado
  │ gera análise contextual (2-3 parágrafos)
  │ retorna JSON response
  ▼
[OpenAIService]
  │ extrai choices[0].message.content
  │ retorna string de análise
  ▼
[SearchController]
  │ model.addAttribute("aiAnalysis", analysis)
  ▼
[search.html]
  │ apresenta análise em caixa destacada
```

**Código envolvido:**

- `OpenAIService.generateAnalysis()` - chamada REST
- `application.properties` - configuração de modelo
- `key.openrouterai.txt` - chave API

---

## 🏗️ Estrutura de Serviços

### **GatewayService** (Cliente RMI)

```java
@Service
public class GatewayService {
    private IGateway gateway; // proxy RMI

    public void connect() {
        // RMI lookup: rmi://localhost:7000/Gateway
        gateway = (IGateway) Naming.lookup(url);
    }

    public List<SearchResult> search(String query, int page) {
        return gateway.search(query, page); // chamada remota
    }
}
```

**Responsabilidades:**

- abstrai comunicação RMI
- reconexão automática se falhar
- encapsula todas as operações remotas

---

### **StatsUpdateService** (Bridge RMI ↔ WebSocket)

```java
@Service
public class StatsUpdateService extends UnicastRemoteObject
                                 implements IStatsListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void updateStats(SystemStats stats) {
        // recebe do Gateway via RMI
        // envia para browsers via WebSocket
        messagingTemplate.convertAndSend("/topic/stats", stats);
    }
}
```

**Responsabilidades:**

- regista-se como RMI Remote Object
- recebe callbacks do Gateway
- distribui para clientes WebSocket

---

### **HackerNewsService** (REST Client)

```java
@Service
public class HackerNewsService {
    private RestTemplate restTemplate;

    public List<HNStory> getTopStoriesWithTerms(String terms, int max) {
        // REST calls para Firebase API
        // filtra por termos no título
        // retorna lista de stories
    }
}
```

**Responsabilidades:**

- chamadas REST ao Hacker News
- filtragem de resultados
- integração para indexação

---

### **OpenAIService** (REST Client)

```java
@Service
public class OpenAIService {
    @Value("${openrouter.model}")
    private String model;

    public String generateAnalysis(String terms, List<SearchResult> results) {
        // constrói prompt com resultados
        // POST para OpenRouter.ai
        // retorna análise textual
    }
}
```

**Responsabilidades:**

- geração de análises IA
- configurável (modelos grátis/pagos)
- gestão de chaves API

---

## 🔌 Pontos de Integração

### **1. RMI (Backend Meta 1)**

- **Protocolo:** Java RMI sobre TCP
- **Porta:** 7000 (configurável)
- **Interface:** `IGateway` (partilhada)
- **Serialização:** Java Serialization
- **Timeout:** configurável por operação

### **2. WebSocket (Frontend ↔ Backend)**

- **Protocolo:** STOMP sobre WebSocket/SockJS
- **Endpoint:** `/ws-stats`
- **Tópico:** `/topic/stats`
- **Fallback:** Long polling (SockJS)
- **Heartbeat:** automático

### **3. REST (APIs Externas)**

- **Hacker News:** Firebase REST API
- **OpenRouter:** OpenAI-compatible API
- **Cliente:** Spring RestTemplate
- **Formato:** JSON

---

## 📊 Configuração de Modelos LLM

### **Modelos Grátis (OpenRouter.ai):**

```properties
# modelo rápido e grátis (recomendado para testes)
openrouter.model=meta-llama/llama-3.1-8b-instruct:free

# alternativa: Google Gemini Flash
openrouter.model=google/gemini-flash-1.5
```

### **Modelos Pagos (melhor qualidade):**

```properties
# Claude 3.5 Sonnet (excelente para análises)
openrouter.model=anthropic/claude-3.5-sonnet

# GPT-4o (OpenAI via OpenRouter)
openrouter.model=openai/gpt-4o

# Llama 3.1 70B (bom custo-benefício)
openrouter.model=meta-llama/llama-3.1-70b-instruct
```

**Ver todos os modelos:** https://openrouter.ai/models

---

## 🔐 Configuração de Chave API

### **Opção 1: Ficheiro (Recomendado)**

```bash
echo "sk-or-v1-xxxxxxxxxx" > key.openrouterai.txt
```

### **Opção 2: application.properties**

```properties
openrouter.api.key=sk-or-v1-xxxxxxxxxx
```

### **Obter Chave:**

1. Criar conta em https://openrouter.ai
2. Ir para https://openrouter.ai/keys
3. Criar nova chave
4. Copiar e guardar em `key.openrouterai.txt`

**Modelos grátis:** não requerem pagamento, apenas chave API

---

## 🎯 Vantagens da Arquitetura

### **Separação de Responsabilidades**

- Backend (Meta 1) não sabe que existe frontend web
- Frontend usa Gateway como qualquer outro cliente
- APIs externas isoladas em serviços dedicados

### **Escalabilidade**

- Múltiplos barrels distribuem carga
- WebSocket evita polling desnecessário
- Cache de resultados no Gateway

### **Interoperabilidade**

- Cliente desktop (Meta 1) e web app acedem mesmos dados
- API REST permite integrações futuras
- Protocolo RMI permite clientes em Java

### **Manutenibilidade**

- MVC facilita alterações de UI
- Serviços testáveis independentemente
- Configuração centralizada

---

## 🐛 Debugging e Logs

```bash
# ver logs do Spring Boot
tail -f logs/spring.log

# verificar conexão RMI
[GatewayService] Conectado ao Gateway RMI: rmi://localhost:7000/Gateway

# confirmar WebSocket
[StatsUpdateService] Registado como listener de estatísticas
WebSocket conectado: CONNECTED

# tracking de análises IA
[OpenAIService] Enviando pedido para OpenRouter.ai com modelo: meta-llama/llama-3.1-8b-instruct:free
```

---

## 📝 Resumo do Fluxo Completo

1. **Utilizador** acede a http://localhost:8080
2. **Thymeleaf** renderiza página inicial
3. **WebSocket** conecta para receber stats em tempo real
4. **Utilizador** pesquisa "java distributed systems"
5. **Controller** recebe pedido HTTP GET
6. **GatewayService** faz RMI call para Gateway
7. **Gateway** distribui entre barrels ativos
8. **Barrels** consultam índice e retornam resultados
9. **Controller** chama HackerNews e OpenRouter (opcional)
10. **Thymeleaf** renderiza página de resultados
11. **Browser** apresenta resultados + análise IA + HN stories
12. **WebSocket** recebe update de estatísticas
13. **JavaScript** atualiza dashboard sem reload

**Tempo total:** ~500ms (pesquisa) + ~2s (análise IA se ativada)
