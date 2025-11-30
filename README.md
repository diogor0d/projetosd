# Googol - Motor de Pesquisa Distribuído

## Descrição do Projeto

Sistema de pesquisa web distribuído com funcionalidades semelhantes ao Google, implementando indexação automática (Web crawler) e motor de busca. Inclui interface web para acesso via browser e integrações com APIs externas (Hacker News e OpenAI).

## Arquitetura do Sistema

### Meta 1 - Backend Distribuído (RMI)

1. **Downloaders** - Crawlers paralelos que obtêm e processam páginas web - mvn compile exec:java@downloader -Dexec.arguments=<id>
2. **Index Storage Barrels** - Servidores replicados que armazenam o índice invertido - mvn compile exec:java@barrel -Dexec.arguments=<id>
3. **RPC/RMI Gateway** - Porta de entrada para clientes - mvn compile exec:java@gateway
4. **RPC/RMI Client** - Interface de linha de comandos - mvn compile exec:java@client

### Meta 2 - Frontend Web (Spring Boot)

5. **Web Application** - Interface web com Thymeleaf e WebSockets - mvn spring-boot:run
   - Dashboard de pesquisa e indexação
   - Estatísticas em tempo real (WebSocket)
   - Integração Hacker News
   - Análise IA com OpenAI
   - REST API para integração programática

## Estrutura do Projeto

```
projetosd/
├── src/
│   ├── common/           # Interfaces e classes comuns (RMI)
│   ├── downloader/       # Web crawlers
│   ├── barrel/           # Storage barrels
│   ├── gateway/          # RPC/RMI Gateway
│   ├── client/           # Cliente linha de comandos
│   ├── queue/            # Gestão de filas de URLs
│   └── frontend/         # 🆕 Aplicação Web (Meta 2)
│       ├── WebApplication.java
│       ├── config/       # Configuração Spring Boot
│       ├── controllers/  # Controladores MVC + REST
│       └── services/     # Serviços (RMI, HN, OpenAI, WebSocket)
├── src/main/resources/   # 🆕 Recursos Spring Boot
│   ├── templates/        # Views Thymeleaf (HTML)
│   ├── static/           # CSS, JavaScript
│   └── application.properties
├── lib/                  # Bibliotecas externas
├── config/               # Ficheiros de configuração
└── data/                 # Dados persistentes
```
