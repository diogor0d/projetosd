# Guia Maven para Projeto Googol

## ✅ Configuração Completa

O projeto está agora configurado para usar **Maven** em vez de compilação manual.

## 📋 Comandos Maven Essenciais

### 1. Compilar o Projeto

```bash
mvn compile
```

### 2. Limpar e Compilar

```bash
mvn clean compile
```

### 3. Criar JAR

```bash
mvn package
```

### 4. Executar Componentes

#### RMI Registry (executar primeiro, em terminal separado)

```bash
rmiregistry 1099
```

#### Storage Barrel

```bash
# Barrel 1
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="1"

# Barrel 2
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="2"
```

#### Gateway

```bash
mvn exec:java -Dexec.mainClass="gateway.Gateway"
```

#### Downloader

```bash
mvn exec:java -Dexec.mainClass="downloader.Downloader" -Dexec.args="1"
```

#### Cliente

```bash
mvn exec:java -Dexec.mainClass="client.GoogolClient"
```

## 🚀 Início Rápido

### Passo 1: Instalar Dependências

```bash
mvn clean install
```

### Passo 2: Iniciar Componentes (em terminais separados)

**Terminal 1 - RMI Registry:**

```bash
rmiregistry 1099
```

**Terminal 2 - Barrel 1:**

```bash
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="1"
```

**Terminal 3 - Barrel 2:**

```bash
mvn exec:java -Dexec.mainClass="barrel.StorageBarrel" -Dexec.args="2"
```

**Terminal 4 - Gateway:**

```bash
mvn exec:java -Dexec.mainClass="gateway.Gateway"
```

**Terminal 5 - Downloader:**

```bash
mvn exec:java -Dexec.mainClass="downloader.Downloader" -Dexec.args="1"
```

**Terminal 6 - Cliente:**

```bash
mvn exec:java -Dexec.mainClass="client.GoogolClient"
```

## 📁 Estrutura de Diretórios Maven

```
projetosd/
├── pom.xml                 # Configuração Maven
├── src/                    # Código fonte
│   ├── common/
│   ├── barrel/
│   ├── gateway/
│   ├── downloader/
│   ├── client/
│   └── queue/
├── target/                 # Gerado pelo Maven (classes compiladas, JARs)
│   ├── classes/           # .class files
│   └── googol-1.0-SNAPSHOT.jar
├── config/
└── data/
```

## 🔧 Comandos Úteis

### Limpar arquivos compilados

```bash
mvn clean
```

### Verificar dependências

```bash
mvn dependency:tree
```

### Executar testes (quando implementar)

```bash
mvn test
```

### Gerar Javadoc

```bash
mvn javadoc:javadoc
```

### Instalar no repositório local

```bash
mvn install
```

## ⚙️ Configuração do pom.xml

O `pom.xml` inclui:

- ✅ **Java 11** como versão alvo
- ✅ **jsoup 1.17.2** para parsing HTML
- ✅ **JUnit 4.13.2** para testes
- ✅ **Maven Compiler Plugin** para compilação
- ✅ **Exec Maven Plugin** para executar classes
- ✅ **Maven JAR Plugin** para criar JARs

## 🎯 Vantagens do Maven

1. **Gestão Automática de Dependências**: jsoup é baixado automaticamente
2. **Build Padronizado**: comandos consistentes
3. **Fácil Integração**: IDEs reconhecem automaticamente
4. **Repositório Central**: acesso a bibliotecas
5. **Testes Automatizados**: suporte nativo para JUnit

## 📝 Notas Importantes

### Não é mais necessário:

- ❌ Baixar jsoup manualmente
- ❌ Configurar classpath manualmente
- ❌ Usar `javac` diretamente
- ❌ Criar scripts de compilação

### Maven faz automaticamente:

- ✅ Download de dependências
- ✅ Configuração do classpath
- ✅ Compilação organizada
- ✅ Gestão de versões

## 🐛 Troubleshooting

### Erro: "mvn command not found"

**Solução:** Instalar Maven:

- Windows: Baixar de https://maven.apache.org/download.cgi
- Mac: `brew install maven`
- Linux: `sudo apt install maven`

### Erro: Porta 1099 já em uso

**Solução:** Matar processo:

```bash
# Windows
netstat -ano | findstr :1099
taskkill /PID <pid> /F

# Linux/Mac
lsof -i :1099
kill -9 <pid>
```

### Erro: "package org.jsoup does not exist"

**Solução:** Executar:

```bash
mvn clean install
```

## 📚 Recursos

- [Maven em 5 Minutos](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
- [Maven Exec Plugin](https://www.mojohaus.org/exec-maven-plugin/)
- [Maven Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)

## 🎓 Próximos Passos

1. Execute `mvn clean install` para baixar dependências
2. Inicie os componentes usando os comandos acima
3. Teste o sistema através do cliente
4. Desenvolva funcionalidades adicionais
