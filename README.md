# Googol - Motor de Pesquisa Distribuído

## Descrição do Projeto

Sistema de pesquisa web distribuído com funcionalidades semelhantes ao Google, implementando indexação automática (Web crawler) e motor de busca.

## Arquitetura do Sistema

### Componentes Principais

1. **Downloaders** - Crawlers paralelos que obtêm e processam páginas web
2. **Index Storage Barrels** - Servidores replicados que armazenam o índice invertido
3. **RPC/RMI Gateway** - Porta de entrada para clientes
4. **RPC/RMI Client** - Interface para utilizadores

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
