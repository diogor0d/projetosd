#!/bin/bash
# exemplos de uso da API REST do Googol

# health check
curl http://localhost:8080/api/health

# pesquisar (GET)
curl "http://localhost:8080/api/search?q=java&page=1"

# indexar URL (POST)
curl -X POST http://localhost:8080/api/index \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com"}'

# obter estatísticas (GET)
curl http://localhost:8080/api/stats

# obter páginas que apontam para um URL (GET)
curl "http://localhost:8080/api/links-to?url=https://example.com"

# exemplo com jq para formatar JSON
curl -s http://localhost:8080/api/stats | jq .

# pesquisa com múltiplas palavras (encode URL)
curl "http://localhost:8080/api/search?q=distributed+systems&page=1"
