#!/bin/bash

# script para inicializar o sistema completo Googol
# executa Gateway, Barrels, Downloaders e Web Application

echo "🚀 Iniciando sistema Googol..."

# cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # sem cor

# função para iniciar componente em background
start_component() {
    local name=$1
    local command=$2
    echo -e "${BLUE}Iniciando $name...${NC}"
    $command &
    sleep 2
}

# garante que está no diretório correto
cd "$(dirname "$0")"

echo ""
echo "=== Meta 1 - Backend RMI ==="

# inicia Gateway
start_component "Gateway" "mvn exec:java@gateway -q"

# inicia Barrels
start_component "Storage Barrel 1" "mvn exec:java@barrel -Dexec.arguments=1 -q"
start_component "Storage Barrel 2" "mvn exec:java@barrel -Dexec.arguments=2 -q"

# inicia Downloaders
start_component "Downloader 1" "mvn exec:java@downloader -Dexec.arguments=1 -q"
start_component "Downloader 2" "mvn exec:java@downloader -Dexec.arguments=2 -q"

echo ""
echo "=== Meta 2 - Web Application ==="
sleep 3

# inicia aplicação web
echo -e "${BLUE}Iniciando Web Application...${NC}"
echo -e "${GREEN}A aplicação web estará disponível em: http://localhost:8080${NC}"
echo ""
echo "Pressiona Ctrl+C para parar todos os componentes"
echo ""

# executa aplicação web em foreground (para poder parar com Ctrl+C)
mvn spring-boot:run

# cleanup ao terminar
echo ""
echo "Parando todos os componentes..."
pkill -f "exec:java@gateway"
pkill -f "exec:java@barrel"
pkill -f "exec:java@downloader"
echo "Sistema parado."
