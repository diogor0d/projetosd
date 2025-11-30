# 🚀 Guia Rápido de Configuração - OpenRouter.ai

## ⚡ Setup em 3 Passos

### 1️⃣ Obter Chave API (Grátis)

1. Acede a **https://openrouter.ai**
2. Clica em "Sign In" (podes usar conta Google/GitHub)
3. Vai a **https://openrouter.ai/keys**
4. Clica "Create Key"
5. Dá um nome (ex: "Googol Project")
6. Copia a chave (formato: `sk-or-v1-...`)

**Nota:** A conta grátis tem créditos iniciais e acesso a modelos grátis!

### 2️⃣ Configurar no Projeto

Cria o ficheiro `key.openrouterai.txt` na raiz do projeto:

```bash
cd /Users/diogo/Documents/LEI/3ano/SD/projetosd
echo "sk-or-v1-TUA_CHAVE_AQUI" > key.openrouterai.txt
```

**Ou** edita `src/main/resources/application.properties`:

```properties
openrouter.api.key=sk-or-v1-TUA_CHAVE_AQUI
```

### 3️⃣ Escolher Modelo

Edita `src/main/resources/application.properties`:

**Opção A - Grátis (recomendado para começar):**

```properties
openrouter.model=meta-llama/llama-3.1-8b-instruct:free
```

**Opção B - Gemini Flash (também grátis):**

```properties
openrouter.model=google/gemini-flash-1.5
```

**Opção C - Claude 3.5 Sonnet (pago, melhor qualidade):**

```properties
openrouter.model=anthropic/claude-3.5-sonnet
```

---

## 📋 Modelos Disponíveis

### 🆓 **Modelos Grátis** (Zero custo)

| Modelo               | ID                                          | Descrição                   |
| -------------------- | ------------------------------------------- | --------------------------- |
| **Llama 3.1 8B**     | `meta-llama/llama-3.1-8b-instruct:free`     | Rápido, boa qualidade geral |
| **Gemini Flash 1.5** | `google/gemini-flash-1.5`                   | Google, muito rápido        |
| **Phi-3 Medium**     | `microsoft/phi-3-medium-128k-instruct:free` | Microsoft, contexto longo   |

### 💰 **Modelos Pagos** (Melhores resultados)

| Modelo                | ID                                  | Custo Aprox     | Descrição            |
| --------------------- | ----------------------------------- | --------------- | -------------------- |
| **Claude 3.5 Sonnet** | `anthropic/claude-3.5-sonnet`       | $3/1M tokens    | Melhor para análises |
| **GPT-4o**            | `openai/gpt-4o`                     | $2.5/1M tokens  | OpenAI flagship      |
| **Llama 3.1 70B**     | `meta-llama/llama-3.1-70b-instruct` | $0.5/1M tokens  | Bom custo-benefício  |
| **Gemini Pro 1.5**    | `google/gemini-pro-1.5`             | $1.25/1M tokens | Google avançado      |

**Ver lista completa:** https://openrouter.ai/models

---

## 🧪 Testar Configuração

### 1. Inicia o sistema:

```bash
# Terminal 1 - Gateway
mvn exec:java@gateway

# Terminal 2 - Barrel
mvn exec:java@barrel -Dexec.arguments=1

# Terminal 3 - Downloader
mvn exec:java@downloader -Dexec.arguments=1

# Terminal 4 - Web App
mvn spring-boot:run
```

### 2. Acede a http://localhost:8080

### 3. Faz uma pesquisa (ex: "java")

### 4. Clica no botão **"🤖 Análise IA"**

Se configurado corretamente, verás uma análise contextual dos resultados!

---

## 📊 Ver Custos (Modelos Pagos)

1. Acede a **https://openrouter.ai/activity**
2. Vê histórico de pedidos e custos
3. Define limites de gastos em **Settings**

**Modelos grátis:** não consomem créditos, mas têm rate limits (ex: 20 pedidos/minuto)

---

## 🔧 Troubleshooting

### ❌ "Análise indisponível: chave da API OpenRouter não configurada"

**Solução:**

- Verifica se `key.openrouterai.txt` existe
- Confirma que a chave começa com `sk-or-v1-`
- Tenta configurar em `application.properties` em vez de ficheiro

### ❌ "Erro ao gerar análise: 401 Unauthorized"

**Solução:**

- Chave API inválida ou expirada
- Gera nova chave em https://openrouter.ai/keys
- Verifica se copiaste a chave completa

### ❌ "Erro ao gerar análise: 429 Too Many Requests"

**Solução:**

- Rate limit excedido (modelos grátis)
- Espera 1 minuto e tenta novamente
- Considera usar modelo pago

### ❌ "Erro ao gerar análise: 402 Payment Required"

**Solução:**

- Modelo escolhido é pago e não tens créditos
- Adiciona créditos em https://openrouter.ai/credits
- Ou usa modelo grátis: `meta-llama/llama-3.1-8b-instruct:free`

### ✅ Ver logs detalhados:

```bash
# Console do Spring Boot mostra:
[OpenAIService] Enviando pedido para OpenRouter.ai com modelo: meta-llama/llama-3.1-8b-instruct:free
```

---

## 💡 Dicas

1. **Começa com modelos grátis** - perfeitos para desenvolvimento e testes
2. **Compara modelos** - cada um tem pontos fortes (velocidade vs qualidade)
3. **Rate limits** - modelos grátis têm limites, planeia testes
4. **Cache de resultados** - a aplicação só chama IA quando pedes explicitamente
5. **Custos baixos** - modelos pagos custam cêntimos por análise

---

## 📚 Recursos

- **Documentação:** https://openrouter.ai/docs
- **Modelos:** https://openrouter.ai/models
- **API Keys:** https://openrouter.ai/keys
- **Pricing:** https://openrouter.ai/docs/pricing
- **Discord:** https://discord.gg/openrouter

---

## ✅ Checklist Final

- [ ] Conta criada em openrouter.ai
- [ ] Chave API obtida e copiada
- [ ] Ficheiro `key.openrouterai.txt` criado
- [ ] Modelo configurado em `application.properties`
- [ ] Sistema backend iniciado (Gateway + Barrel + Downloader)
- [ ] Web app iniciada (`mvn spring-boot:run`)
- [ ] Pesquisa realizada com sucesso
- [ ] Botão "🤖 Análise IA" testado
- [ ] Análise gerada e apresentada

**Tudo OK?** Agora tens IA integrada no teu motor de pesquisa! 🎉
