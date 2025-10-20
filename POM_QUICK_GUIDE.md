# 🎯 POM.XML - Visão Rápida

## Estrutura Visual do pom.xml

```
┌─────────────────────────────────────────────────────────┐
│                      pom.xml                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  📋 IDENTIFICAÇÃO (Quem é o projeto?)                  │
│  ├─ groupId: pt.uc.dei.sd                             │
│  ├─ artifactId: googol                                │
│  └─ version: 1.0-SNAPSHOT                             │
│                                                         │
│  ⚙️  PROPRIEDADES (Configurações)                       │
│  ├─ Java 11                                            │
│  ├─ UTF-8                                              │
│  └─ jsoup.version = 1.17.2                            │
│                                                         │
│  📦 DEPENDÊNCIAS (Bibliotecas que precisa)             │
│  ├─ jsoup 1.17.2     → parsing HTML                   │
│  └─ JUnit 4.13.2     → testes                         │
│                                                         │
│  🔧 BUILD (Como compilar e executar)                   │
│  ├─ Compiler Plugin  → compila Java 11                │
│  ├─ JAR Plugin       → cria googol-1.0-SNAPSHOT.jar   │
│  └─ Exec Plugin      → executa classes                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Fluxo: Como Maven Usa o pom.xml

```
1. Você executa: mvn install
         ↓
2. Maven lê: pom.xml
         ↓
3. Identifica dependências:
   ├─ jsoup 1.17.2
   └─ junit 4.13.2
         ↓
4. Verifica repositório local (~/.m2/)
   ├─ Tem jsoup? → SIM: usa cache
   └─ Tem junit? → NÃO: baixa da internet
         ↓
5. Baixa jsoup de:
   https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.17.2/
         ↓
6. Salva em:
   ~/.m2/repository/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
         ↓
7. Compila código:
   src/ → target/classes/
         ↓
8. Cria JAR:
   target/googol-1.0-SNAPSHOT.jar
         ↓
9. ✅ PRONTO!
```

## Comparação: Sem Maven vs Com Maven

### ❌ SEM MAVEN (Manual)
```
1. Google: "download jsoup jar"
2. Baixar jsoup-1.17.2.jar
3. Criar pasta lib/
4. Copiar jar para lib/
5. javac -cp "lib/*;src" -d bin src/**/*.java
6. java -cp "bin;lib/*" client.GoogolClient
7. Repetir para cada biblioteca...
```

### ✅ COM MAVEN (Automático)
```
1. Adicionar 5 linhas no pom.xml
2. mvn install
3. mvn exec:java -Dexec.mainClass="client.GoogolClient"
4. Pronto! 🎉
```

## Anatomia de uma Dependência

```xml
<dependency>
    <groupId>org.jsoup</groupId>        ← Organização
    <artifactId>jsoup</artifactId>       ← Biblioteca
    <version>1.17.2</version>            ← Versão
</dependency>

Tradução: "Quero usar jsoup versão 1.17.2 da organização org.jsoup"
```

**Maven transforma isso em:**
- URL: `https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar`
- Local: `~/.m2/repository/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar`

## Ciclo de Vida Maven (Simplificado)

```
mvn clean
  ↓
  Apaga target/
  
mvn compile
  ↓
  src/ → target/classes/
  
mvn test
  ↓
  Executa testes JUnit
  
mvn package
  ↓
  Cria googol-1.0-SNAPSHOT.jar
  
mvn install
  ↓
  Instala em ~/.m2/repository/
```

## Comandos Essenciais

```bash
# Primeira vez (baixa dependências)
mvn clean install

# Compilar após mudanças
mvn compile

# Executar Storage Barrel
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="1"

# Executar Gateway
mvn exec:java -Dexec.mainClass="gateway.Gateway"

# Executar Cliente
mvn exec:java -Dexec.mainClass="client.GoogolClient"

# Ver dependências
mvn dependency:tree
```

## Onde Ficam os Ficheiros?

```
SEU PROJETO
projetosd/
├── pom.xml                     ← Configuração
├── src/                        ← Seu código
│   └── barrel/StorageBarrel.java
└── target/                     ← Gerado por Maven
    ├── classes/                ← .class compilados
    │   └── barrel/StorageBarrel.class
    └── googol-1.0-SNAPSHOT.jar

REPOSITÓRIO LOCAL MAVEN
~/.m2/repository/
└── org/jsoup/jsoup/1.17.2/
    └── jsoup-1.17.2.jar        ← Biblioteca baixada
```

## ⚡ Resumo de 30 Segundos

**pom.xml é:**
- 📝 Ficheiro de configuração do Maven
- 🎯 Define: quem é o projeto, que bibliotecas usa, como compilar
- 🤖 Automatiza: download de bibliotecas, compilação, criação de JAR
- 🔑 Essencial: sem ele, Maven não sabe o que fazer

**Para o seu projeto:**
- ✅ Já está configurado corretamente
- ✅ jsoup será baixado automaticamente
- ✅ Só precisa executar: `mvn clean install`
- ✅ Depois pode executar os componentes com `mvn exec:java`

## 📌 Próximo Passo

Execute no terminal:
```powershell
mvn clean install
```

Isso vai configurar tudo automaticamente! 🚀
