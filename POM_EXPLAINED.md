# 📘 Guia Completo do POM.XML

## O que é o POM.XML?

**POM** significa **Project Object Model** (Modelo de Objeto do Projeto). É o ficheiro fundamental do Maven que contém:

- Informação sobre o projeto
- Configurações de build
- Dependências (bibliotecas externas)
- Plugins para tarefas específicas

Pense nele como a "certidão de nascimento" e "manual de instruções" do seu projeto.

---

## 📋 Estrutura Detalhada

### 1. **Cabeçalho XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
```

**Explicação:**

- Declara que é um ficheiro XML
- Define namespaces do Maven
- `modelVersion`: Versão do modelo POM (sempre 4.0.0)

---

### 2. **Coordenadas do Projeto** (Identificação Única)

```xml
<groupId>pt.uc.dei.sd</groupId>
<artifactId>googol</artifactId>
<version>1.0-SNAPSHOT</version>
<packaging>jar</packaging>
```

**Explicação:**

- **`groupId`**: Identifica a organização (como um domínio invertido)
  - `pt.uc.dei.sd` = Universidade de Coimbra, DEI, Sistemas Distribuídos
- **`artifactId`**: Nome do projeto
  - `googol` = nome do seu motor de pesquisa
- **`version`**: Versão do projeto
  - `1.0-SNAPSHOT` = versão em desenvolvimento (ainda não estável)
  - Quando finalizar, seria `1.0` ou `1.0.0`
- **`packaging`**: Tipo de arquivo final
  - `jar` = Java ARchive (ficheiro executável Java)

**Analogia:** É como o RG do projeto

- groupId = País/Cidade
- artifactId = Nome
- version = Data de emissão

---

### 3. **Metadados do Projeto**

```xml
<name>Googol Search Engine</name>
<description>Motor de pesquisa distribuído usando RMI</description>
```

**Explicação:**

- **`name`**: Nome legível do projeto
- **`description`**: Breve descrição
- Opcional, mas útil para documentação

---

### 4. **Propriedades** (Configurações Globais)

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <jsoup.version>1.17.2</jsoup.version>
</properties>
```

**Explicação:**

- **`project.build.sourceEncoding`**: Codificação dos ficheiros (UTF-8 para suportar acentos)
- **`maven.compiler.source`**: Versão do Java no código fonte (Java 11)
- **`maven.compiler.target`**: Versão Java do bytecode compilado (Java 11)
- **`jsoup.version`**: Variável customizada para versão do jsoup

**Vantagem:** Usar `${jsoup.version}` em vez de repetir `1.17.2` em vários lugares

---

### 5. **Dependências** (Bibliotecas Externas)

```xml
<dependencies>
    <!-- jsoup para parsing HTML -->
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>${jsoup.version}</version>
    </dependency>

    <!-- JUnit para testes (opcional) -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Explicação:**

#### Dependência 1: jsoup

- **O que faz:** Baixa automaticamente a biblioteca jsoup da internet
- **groupId + artifactId + version:** Identifica EXATAMENTE qual biblioteca
- **Onde baixa:** Maven Central Repository (https://mvnrepository.com/)

#### Dependência 2: JUnit

- **O que faz:** Biblioteca para testes unitários
- **`scope`:** `test` = só usada durante testes, não incluída no JAR final

**Como funciona:**

1. Você executa `mvn install`
2. Maven lê essas dependências
3. Verifica se já tem localmente (em `~/.m2/repository/`)
4. Se não tiver, baixa automaticamente
5. Adiciona ao classpath do projeto

**Vantagem vs Manual:**

```
❌ Manual: Baixar jsoup.jar, colocar em lib/, configurar classpath
✅ Maven:  Apenas adicionar 5 linhas no pom.xml
```

---

### 6. **Build** (Configuração de Compilação)

```xml
<build>
    <sourceDirectory>src</sourceDirectory>
```

**Explicação:**

- **`sourceDirectory`**: Onde está o código fonte
- Padrão Maven: `src/main/java`
- No nosso caso: `src` (estrutura customizada)

---

### 7. **Plugins** (Ferramentas de Build)

#### Plugin 1: Compiler Plugin

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>11</source>
        <target>11</target>
    </configuration>
</plugin>
```

**O que faz:**

- Compila o código Java
- `source=11`: Aceita sintaxe do Java 11
- `target=11`: Gera bytecode compatível com Java 11

**Equivalente manual:**

```bash
javac -source 11 -target 11 *.java
```

---

#### Plugin 2: JAR Plugin

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.3.0</version>
</plugin>
```

**O que faz:**

- Cria o ficheiro `.jar` com as classes compiladas
- Executa quando roda `mvn package`

**Equivalente manual:**

```bash
jar cvf googol.jar -C bin .
```

---

#### Plugin 3: Exec Maven Plugin

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>barrel</id>
            <configuration>
                <mainClass>barrel.StorageBarrel</mainClass>
            </configuration>
        </execution>
        <!-- ... outras execuções ... -->
    </executions>
</plugin>
```

**O que faz:**

- Permite executar classes Java diretamente com Maven
- Define configurações pré-definidas para cada componente

**Como usar:**

```bash
# Em vez de:
java -cp target/classes barrel.StorageBarrel 1

# Você faz:
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="1"
```

**Cada `<execution>`:**

- `id`: Nome identificador
- `mainClass`: Qual classe tem o método `main()` a executar

---

## 🔄 Ciclo de Vida do Maven

Quando você executa comandos Maven, ele segue estas fases:

```
mvn compile
    ↓
1. validate    → Valida o pom.xml
2. compile     → Compila código fonte (src/) para target/classes/
    ↓
✅ PARA AQUI

mvn package
    ↓
1. validate
2. compile
3. test        → Executa testes
4. package     → Cria o JAR em target/
    ↓
✅ PARA AQUI

mvn install
    ↓
1. validate
2. compile
3. test
4. package
5. install     → Instala JAR no repositório local (~/.m2/)
    ↓
✅ PARA AQUI
```

---

## 📁 Estrutura de Diretórios Maven

```
projetosd/
├── pom.xml                    ← Ficheiro de configuração Maven
├── src/                       ← Código fonte (definido no pom.xml)
│   ├── common/
│   ├── barrel/
│   └── ...
└── target/                    ← GERADO pelo Maven (não versionar!)
    ├── classes/              ← Ficheiros .class compilados
    │   ├── common/
    │   ├── barrel/
    │   └── ...
    └── googol-1.0-SNAPSHOT.jar  ← JAR final
```

---

## 💡 Como o Maven Resolve Dependências

### Exemplo: jsoup

1. **Você adiciona no pom.xml:**

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

2. **Maven faz:**

   - Verifica repositório local: `~/.m2/repository/org/jsoup/jsoup/1.17.2/`
   - Se não existir, baixa de: https://repo.maven.apache.org/maven2/
   - Salva em: `~/.m2/repository/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar`
   - Adiciona ao classpath automaticamente

3. **Você usa no código:**

```java
import org.jsoup.Jsoup;  // ✅ Funciona automaticamente!
```

---

## 🎯 Comandos Práticos com Explicação

### `mvn clean`

- **O que faz:** Apaga a pasta `target/`
- **Quando usar:** Antes de recompilar tudo do zero

### `mvn compile`

- **O que faz:** Compila código em `src/` → `target/classes/`
- **Quando usar:** Para compilar após mudanças no código

### `mvn package`

- **O que faz:** `compile` + cria JAR em `target/`
- **Quando usar:** Para criar distribuível do projeto

### `mvn install`

- **O que faz:** `package` + instala no repositório local
- **Quando usar:** Primeira vez ou após mudança em dependências

### `mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="1"`

- **O que faz:** Executa a classe `barrel.StorageBarrel` com argumento `"1"`
- **Quando usar:** Para executar componentes do sistema

### `mvn dependency:tree`

- **O que faz:** Mostra árvore de dependências
- **Quando usar:** Para ver todas as bibliotecas (diretas e transitivas)

---

## 🔧 Modificações Comuns

### Adicionar Nova Dependência

1. Procurar no Maven Central: https://mvnrepository.com/
2. Copiar coordenadas
3. Adicionar em `<dependencies>`:

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

4. Executar: `mvn install`

### Mudar Versão do Java

```xml
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

### Criar JAR Executável

Adicionar em `<build>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <archive>
            <manifest>
                <mainClass>client.GoogolClient</mainClass>
            </manifest>
        </archive>
        <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
    </configuration>
</plugin>
```

---

## ✅ Resumo: Vantagens do pom.xml

| Sem Maven                  | Com Maven (pom.xml)     |
| -------------------------- | ----------------------- |
| Baixar JARs manualmente    | Automático              |
| Configurar classpath       | Automático              |
| Compilar com javac         | `mvn compile`           |
| Criar JAR com jar          | `mvn package`           |
| Gerir versões manualmente  | Centralizado no pom.xml |
| Scripts de build complexos | Comandos padronizados   |

---

## 📚 Recursos Adicionais

- **Maven em 5 Minutos:** https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
- **Maven Repository:** https://mvnrepository.com/
- **POM Reference:** https://maven.apache.org/pom.html
- **Plugin List:** https://maven.apache.org/plugins/index.html

---

## 🎓 Para o Seu Projeto Googol

**O pom.xml atual configura:**
✅ Java 11  
✅ jsoup (download automático)  
✅ JUnit (testes)  
✅ Compilação automática  
✅ Criação de JAR  
✅ Comandos para executar cada componente

**Próximo passo:**

```bash
mvn clean install
```

Isso vai:

1. Baixar jsoup automaticamente
2. Compilar todo o projeto
3. Criar o JAR
4. Ficar pronto para executar!
