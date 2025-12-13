#!/bin/bash

# Script para parar todos os componentes do sistema Googol
# Uso: ./stop-system.sh

echo "A parar o sistema de pesquisa Googol..."
echo "=========================================="

# Funcao para verificar se existem processos em execucao
check_processes() {
    ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | wc -l | tr -d ' '
}

# Obter todos os PIDs para os componentes do Googol
PIDS=$(ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "Nenhum processo do Googol em execucao."
    exit 0
fi

echo "Foram encontrados os seguintes processos:"
ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | while read line; do
    pid=$(echo "$line" | awk '{print $2}')
    if echo "$line" | grep -q "spring-boot:run"; then
        echo "  - Aplicacao Web Spring Boot (PID $pid)"
    elif echo "$line" | grep -q "exec:java@gateway"; then
        echo "  - Gateway (PID $pid)"
    elif echo "$line" | grep -q "exec:java@barrel"; then
        echo "  - Barrel de Armazenamento (PID $pid)"
    elif echo "$line" | grep -q "exec:java@downloader"; then
        echo "  - Downloader (PID $pid)"
    fi
done

echo ""
echo "A enviar sinal de terminacao (SIGTERM)..."

# Tentar desligar de forma controlada primeiro
for PID in $PIDS; do
    kill $PID 2>/dev/null
done

# Aguardar que os processos terminem de forma controlada
echo -n "A aguardar a terminacao dos processos"
for i in {1..10}; do
    sleep 1
    echo -n "."
    REMAINING=$(check_processes)
    if [ "$REMAINING" -eq 0 ]; then
        break
    fi
done
echo ""

# Verificar se ainda existem processos em execucao
REMAINING_PIDS=$(ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | awk '{print $2}')

if [ -n "$REMAINING_PIDS" ]; then
    echo "Alguns processos nao terminaram de forma controlada. A forcar terminacao..."
    for PID in $REMAINING_PIDS; do
        kill -9 $PID 2>/dev/null
    done
    sleep 1
fi

# Verificacao final
FINAL_COUNT=$(check_processes)
if [ "$FINAL_COUNT" -eq 0 ]; then
    echo "Todos os componentes do sistema Googol foram terminados com sucesso!"
else
    echo "Aviso: Alguns processos podem ainda estar em execucao."
    echo "   Execute 'ps aux | grep java' para verificar manualmente."
    exit 1
fi

echo ""
echo "Sistema parado em $(date '+%Y-%m-%d %H:%M:%S')"
