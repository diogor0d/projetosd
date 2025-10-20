# Guia de Execução do Sistema Googol

## Pré-requisitos

- Java JDK 11 ou superior
- Biblioteca jsoup (colocar o JAR na pasta `lib/`)

## Download da biblioteca jsoup

1. Aceder a https://jsoup.org/download
2. Baixar o ficheiro `jsoup-X.X.X.jar`
3. Colocar na pasta `lib/`

## Compilação

### Compilar todos os componentes:

```bash
# Linux/Mac
javac -d bin -cp "lib/*:src" src/common/*.java src/barrel/*.java src/gateway/*.java src/downloader/*.java src/client/*.java src/queue/*.java

# Windows
javac -d bin -cp "lib/*;src" src/common/*.java src/barrel/*.java src/gateway/*.java src/downloader/*.java src/client/*.java src/queue/*.java
```

## Execução

### 1. Iniciar RMI Registry (numa janela de terminal)

```bash
rmiregistry 1099
```

### 2. Iniciar Storage Barrels (em terminais separados)

```bash
# Barrel 1
java -cp bin barrel.StorageBarrel 1

# Barrel 2
java -cp bin barrel.StorageBarrel 2
```

### 3. Iniciar Gateway

```bash
java -cp bin gateway.Gateway
```

### 4. Iniciar Downloaders (em terminais separados)

```bash
# Downloader 1
java -cp "bin:lib/*" downloader.Downloader 1

# Downloader 2
java -cp "bin:lib/*" downloader.Downloader 2

# Windows: usar ; em vez de :
java -cp "bin;lib/*" downloader.Downloader 1
```

### 5. Iniciar Cliente

```bash
java -cp bin client.GoogolClient
```

## Ordem Recomendada de Inicialização

1. RMI Registry
2. Storage Barrels (pelo menos 1)
3. Gateway
4. Downloaders
5. Cliente

## Resolução de Problemas

### Erro: "Connection refused"

- Verificar se o RMI Registry está a correr na porta 1099
- Verificar se os componentes estão a usar o hostname correto

### Erro: "ClassNotFoundException"

- Verificar se todos os ficheiros foram compilados
- Verificar o classpath nas execuções

### Erro: jsoup não encontrado

- Colocar o JAR do jsoup na pasta `lib/`
- Incluir `lib/*` no classpath ao executar o Downloader

## Testes Básicos

### Teste 1: Indexar URL manualmente

1. No cliente, escolher opção 1
2. Inserir URL: `https://www.uc.pt`
3. Aguardar processamento

### Teste 2: Realizar pesquisa

1. No cliente, escolher opção 2
2. Inserir termos: `universidade coimbra`
3. Navegar pelos resultados

### Teste 3: Ver estatísticas

1. No cliente, escolher opção 4
2. Verificar barrels ativos e pesquisas comuns

## Notas

- Os Barrels devem ser iniciados antes da Gateway
- A Gateway tenta conectar-se aos Barrels na inicialização
- Múltiplos Downloaders podem trabalhar em paralelo
- O sistema é tolerante a falhas: se um Barrel falhar, a Gateway usa outro
