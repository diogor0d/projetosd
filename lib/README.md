# Download e Configuração do jsoup

## Opção 1: Download Manual

1. Aceder ao site oficial: https://jsoup.org/download
2. Fazer download da versão mais recente (ex: `jsoup-1.17.2.jar`)
3. Colocar o ficheiro JAR na pasta `lib/` do projeto

## Opção 2: Maven (alternativa)

Se estiver a usar Maven, adicionar ao `pom.xml`:

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

## Opção 3: Download via comando (Linux/Mac)

```bash
cd lib/
wget https://jsoup.org/packages/jsoup-1.17.2.jar
```

## Opção 4: PowerShell (Windows)

```powershell
cd lib
Invoke-WebRequest -Uri "https://jsoup.org/packages/jsoup-1.17.2.jar" -OutFile "jsoup-1.17.2.jar"
```

## Verificação

Após download, a estrutura deve ser:

```
projetosd/
├── lib/
│   └── jsoup-1.17.2.jar
├── src/
│   └── ...
└── ...
```

## Uso no Projeto

### Compilação

```bash
javac -cp "lib/*:src" -d bin src/**/*.java
```

### Execução

```bash
java -cp "bin:lib/*" downloader.Downloader
```

**Nota:** No Windows, usar `;` em vez de `:` no classpath.

## Documentação jsoup

- Site oficial: https://jsoup.org/
- Cookbook: https://jsoup.org/cookbook/
- API docs: https://jsoup.org/apidocs/

## Exemplo de Uso Básico

```java
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

Document doc = Jsoup.connect("https://example.com")
    .timeout(5000)
    .userAgent("Mozilla/5.0")
    .get();

String title = doc.title();
String text = doc.body().text();
Elements links = doc.select("a[href]");
```
