# 🎨 Diagramas Visuais do Sistema

## 1. Fluxo de Pesquisa Completo

```
┌─────────────┐
│   Browser   │ 1. GET /search?q=java
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────┐
│      SearchController (MVC)              │
│  ┌────────────────────────────────────┐ │
│  │ 1. Valida parâmetros               │ │
│  │ 2. Chama GatewayService.search()  │ │
│  └────────────────────────────────────┘ │
└──────┬──────────────────────────────────┘
       │ RMI
       ▼
┌─────────────────────────────────────────┐
│     Gateway (RMI Server - Meta 1)       │
│  ┌────────────────────────────────────┐ │
│  │ 1. Load balance entre barrels     │ │
│  │ 2. Distribui query                │ │
│  │ 3. Agrega resultados              │ │
│  │ 4. Ordena por relevância          │ │
│  │ 5. Atualiza stats                 │ │
│  └────────────────────────────────────┘ │
└──┬───────┬──────────────────────────────┘
   │       │
   │       └──────────────┐
   │                      │
   ▼                      ▼
┌─────────┐          ┌─────────┐
│ Barrel1 │          │ Barrel2 │
│ (Índice)│          │ (Índice)│
└─────────┘          └─────────┘
   │                      │
   └──────────┬───────────┘
              │ SearchResult[]
              ▼
┌─────────────────────────────────────────┐
│      SearchController (MVC)              │
│  ┌────────────────────────────────────┐ │
│  │ 3. Se aiAnalysis=true:            │ │
│  │    → OpenAIService.generate()     │ │
│  │ 4. HackerNewsService.getStories() │ │
│  │ 5. Adiciona tudo ao Model         │ │
│  └────────────────────────────────────┘ │
└──────┬──────────────────────────────────┘
       │
       ├─────────────────┐
       │                 │
       ▼                 ▼
┌──────────────┐  ┌──────────────────┐
│ OpenRouter   │  │  Hacker News API │
│ (análise IA) │  │  (stories)       │
└──────┬───────┘  └────────┬─────────┘
       │                   │
       └─────────┬─────────┘
                 │
                 ▼
       ┌──────────────────┐
       │ Thymeleaf Engine │
       │ (search.html)    │
       └─────────┬────────┘
                 │
                 ▼
       ┌──────────────────┐
       │  HTML Response   │
       │  - Resultados    │
       │  - Análise IA    │
       │  - HN Stories    │
       └─────────┬────────┘
                 │
                 ▼
           ┌─────────────┐
           │   Browser   │
           └─────────────┘
```

---

## 2. Fluxo de Estatísticas em Tempo Real (WebSocket)

```
INICIALIZAÇÃO:
═══════════════

┌─────────────┐                    ┌──────────────────┐
│   Browser   │ WS Connect         │  Spring Boot     │
│             │───────────────────▶│  WebSocket       │
│             │ /ws-stats          │  Config          │
└─────────────┘                    └──────────────────┘
                                            │
                                            │ Aceita
                                            ▼
                                   ┌──────────────────┐
                                   │ StatsUpdate      │
                                   │ Service          │
                                   │ (RMI Listener)   │
                                   └────────┬─────────┘
                                            │
                                            │ RMI: registerStatsListener()
                                            ▼
                                   ┌──────────────────┐
                                   │   Gateway RMI    │
                                   │   (Meta 1)       │
                                   └──────────────────┘


PUSH DE ESTATÍSTICAS:
═══════════════════════

┌──────────────────┐
│   Gateway RMI    │ 1. Detecta mudança (nova pesquisa, barrel ativo, etc)
└────────┬─────────┘
         │
         │ 2. Para cada listener:
         │    listener.updateStats(newStats)
         ▼
┌──────────────────────────────────────────┐
│      StatsUpdateService                   │
│  @Override updateStats(SystemStats stats) │
└────────┬─────────────────────────────────┘
         │
         │ 3. messagingTemplate.send("/topic/stats", stats)
         ▼
┌──────────────────┐
│  WebSocket       │ 4. Serializa para JSON
│  Broker (STOMP)  │    Envia para todos subscritos
└────────┬─────────┘
         │
         │ 5. Push via WebSocket
         ▼
┌─────────────────────────────────────────┐
│   Browser (stats-websocket.js)          │
│  stompClient.subscribe('/topic/stats')  │
│  ┌────────────────────────────────────┐ │
│  │ function(message) {                │ │
│  │   stats = JSON.parse(message.body) │ │
│  │   updateStatsUI(stats)             │ │
│  │ }                                  │ │
│  └────────────────────────────────────┘ │
└─────────────────┬───────────────────────┘
                  │
                  │ 6. Atualiza DOM
                  ▼
         ┌─────────────────┐
         │   UI Updated    │
         │   (sem reload!) │
         └─────────────────┘

TEMPO: ~50ms do Gateway até UI atualizar!
```

---

## 3. Integração com APIs Externas

```
HACKER NEWS:
═══════════

SearchController ──▶ HackerNewsService
                          │
                          │ GET /v0/topstories.json
                          ▼
                    ┌──────────────┐
                    │ Firebase API │
                    └──────┬───────┘
                           │ [12345, 12346, ...]
                           ▼
                    HackerNewsService
                           │
                           │ Para cada ID:
                           │ GET /v0/item/{id}.json
                           ▼
                    ┌──────────────┐
                    │ Firebase API │
                    └──────┬───────┘
                           │ {title, url, score, ...}
                           ▼
                    HackerNewsService
                           │
                           │ Filtra por termos
                           │ Retorna HNStory[]
                           ▼
                    SearchController
                           │
                           │ model.add("hackerNewsStories")
                           ▼
                        search.html



OPENROUTER.AI (LLM):
═══════════════════

SearchController ──▶ OpenAIService
                          │
                          │ 1. Constrói prompt com resultados
                          │
                          │ POST /api/v1/chat/completions
                          │ {
                          │   "model": "meta-llama/llama-3.1-8b-instruct:free",
                          │   "messages": [{
                          │     "role": "user",
                          │     "content": "Analisa resultados..."
                          │   }],
                          │   "max_tokens": 500
                          │ }
                          ▼
                    ┌──────────────┐
                    │ OpenRouter   │
                    │   API        │
                    └──────┬───────┘
                           │
                           │ Roteia para modelo LLM
                           ▼
                    ┌──────────────┐
                    │ Meta Llama   │ (ou outro modelo)
                    │   3.1 8B     │
                    └──────┬───────┘
                           │
                           │ Gera análise (2-3 parágrafos)
                           ▼
                    ┌──────────────┐
                    │ OpenRouter   │
                    │   API        │
                    └──────┬───────┘
                           │ {
                           │   "choices": [{
                           │     "message": {
                           │       "content": "Análise..."
                           │     }
                           │   }]
                           │ }
                           ▼
                    OpenAIService
                           │
                           │ Extrai content
                           │ Retorna string
                           ▼
                    SearchController
                           │
                           │ model.add("aiAnalysis")
                           ▼
                        search.html

TEMPO: HackerNews ~2s | OpenRouter ~3-5s (dependendo do modelo)
```

---

## 4. Arquitetura de Camadas

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌────────────┐  ┌────────────┐  ┌───────────────────────┐  │
│  │ Templates  │  │ Static CSS │  │  JavaScript (WS)      │  │
│  │ (Thymeleaf)│  │     JS     │  │  (stats-websocket.js) │  │
│  └────────────┘  └────────────┘  └───────────────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            │ HTTP/WebSocket
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    CONTROLLER LAYER (MVC)                    │
│  ┌────────────────────┐         ┌──────────────────────┐    │
│  │ SearchController   │         │   ApiController      │    │
│  │ (HTML Views)       │         │   (REST JSON)        │    │
│  └─────────┬──────────┘         └──────────┬───────────┘    │
└────────────┼───────────────────────────────┼────────────────┘
             │                               │
             │                               │
             ▼                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     SERVICE LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │
│  │ Gateway      │  │ HackerNews   │  │ OpenAI          │   │
│  │ Service      │  │ Service      │  │ Service         │   │
│  │ (RMI Client) │  │ (REST)       │  │ (REST)          │   │
│  └──────┬───────┘  └──────┬───────┘  └────────┬────────┘   │
│         │                 │                    │             │
│  ┌──────┴───────────────┐ │                    │             │
│  │ StatsUpdateService   │ │                    │             │
│  │ (RMI + WebSocket)    │ │                    │             │
│  └──────────────────────┘ │                    │             │
└────────────┬───────────────┼────────────────────┼────────────┘
             │               │                    │
             │ RMI           │ HTTP               │ HTTP
             ▼               ▼                    ▼
┌──────────────────┐  ┌──────────────┐  ┌─────────────────┐
│   Gateway RMI    │  │ HackerNews   │  │  OpenRouter.ai  │
│   (Meta 1)       │  │ Firebase API │  │  (LLM Models)   │
└────────┬─────────┘  └──────────────┘  └─────────────────┘
         │
         │ RMI
         ▼
┌──────────────────┐
│ Storage Barrels  │
│ (Meta 1)         │
└──────────────────┘
```

---

## 5. Ciclo de Vida de uma Requisição HTTP

```
EXEMPLO: Pesquisa "java" com análise IA

1. Browser                  → GET /search?q=java&aiAnalysis=true
2. DispatcherServlet        → Roteia para SearchController
3. SearchController.search()→ Extrai parâmetros

4. GatewayService           → RMI call: gateway.search("java", 1)
5. Gateway (Meta 1)         → Distribui entre 2 barrels
6. Barrel1 + Barrel2        → Consulta índice local
7. Gateway                  → Agrega, ordena, pagina resultados
8. SearchController         → Recebe List<SearchResult>

9. OpenAIService            → POST https://openrouter.ai/...
10. OpenRouter              → Gera análise com Llama 3.1
11. SearchController        → Recebe análise textual

12. HackerNewsService       → GET top stories, filtra por "java"
13. SearchController        → Recebe List<HNStory>

14. Model                   → Adiciona: results, aiAnalysis, hnStories
15. ViewResolver            → Seleciona search.html
16. Thymeleaf               → Renderiza template com dados
17. DispatcherServlet       → Retorna HTML gerado
18. Browser                 → Apresenta página

TEMPO TOTAL: ~3-5 segundos (maioria aguardando OpenRouter)
             ~500ms sem análise IA
```

---

## 6. Estados do WebSocket

```
┌─────────────┐
│  CONNECTING │ ← Browser abre conexão /ws-stats
└──────┬──────┘
       │ SockJS handshake
       ▼
┌─────────────┐
│  CONNECTED  │ ← STOMP protocol estabelecido
└──────┬──────┘   Indicador verde pulsante 🟢
       │
       │ subscribe('/topic/stats')
       ▼
┌─────────────┐
│  SUBSCRIBED │ ← Aguarda mensagens do servidor
└──────┬──────┘
       │
       │ ┌──────────────────┐
       │ │ Novo evento:     │
       │ │ - Pesquisa nova  │
       │ │ - Barrel ativo   │
       │ │ - Tempo mudou    │
       │ └─────────┬────────┘
       │           │
       │◄──────────┘ Gateway detecta
       │
       │ Gateway.notifyListeners(stats)
       │           │
       │           ▼
       │   StatsUpdateService.updateStats(stats)
       │           │
       │           ▼
       │   WebSocket.send("/topic/stats", stats)
       │           │
       ▼           ▼
┌─────────────┐
│  MESSAGE    │ ← Recebe JSON com novas stats
│  RECEIVED   │   updateStatsUI(stats)
└──────┬──────┘   DOM atualizado sem reload!
       │
       └────────▶ SUBSCRIBED (aguarda próxima)


ERRO/DESCONEXÃO:
═══════════════

┌─────────────┐
│  ERROR /    │ ← Conexão perdida
│  CLOSED     │   Indicador vermelho 🔴
└──────┬──────┘
       │ setTimeout(5000)
       ▼
┌─────────────┐
│ RECONNECTING│ ← Tenta reconectar
└──────┬──────┘
       │
       └────▶ volta a CONNECTING


FALLBACK (Polling):
═══════════════════

Se WebSocket falha completamente:

@Scheduled(fixedRate = 5000)
pollStats() {
    stats = gatewayService.getSystemStats()
    if (stats != lastStats) {
        updateStats(stats) → envia via WebSocket
    }
}

Garante atualizações mesmo sem push ativo!
```

---

## Legenda de Símbolos

- `→` HTTP Request / Method call
- `▼` Fluxo descendente
- `│` Dependência / Conexão
- `═` Secção importante
- `┌─┐` Componente / Camada
- `🟢` Conexão ativa
- `🔴` Erro / Desconectado
- `RMI` Java Remote Method Invocation
- `WS` WebSocket
