# Googol - Motor de Pesquisa Distribuído

## Descrição do Projeto

Sistema de pesquisa web distribuído com funcionalidades semelhantes ao Google, implementando indexação automática (Web crawler) e motor de busca.

## Arquitetura do Sistema

### Componentes Principais

1. **Downloaders** - Crawlers paralelos que obtêm e processam páginas web - mvn compile exec:java@downloader -Dexec.arguments=<id>
2. **Index Storage Barrels** - Servidores replicados que armazenam o índice invertido - mvn compile exec:java@barrel -Dexec.arguments=<id>
3. **RPC/RMI Gateway** - Porta de entrada para clientes - mvn compile exec:java@gateway
4. **RPC/RMI Client** - Interface para utilizadores - mvn compile exec:java@client

## Estrutura do Projeto

```
projetosd/
├── src/
│   ├── common/           # Interfaces e classes comuns
│   ├── downloader/       # Web crawlers
│   ├── barrel/           # Storage barrels
│   ├── gateway/          # RPC/RMI Gateway
│   ├── client/           # Cliente
│   └── queue/            # Gestão de filas de URLs
├── lib/                  # Bibliotecas externas (jsoup, etc)
├── config/               # Ficheiros de configuração
└── data/                 # Dados persistentes
```
