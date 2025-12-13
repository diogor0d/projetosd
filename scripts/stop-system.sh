#!/bin/bash

# Script para parar todos os componentes do sistema Googol
# Usage: ./stop-system.sh

echo "🛑 Stopping Googol Search Engine System..."
echo "=========================================="

# Function to check if any processes are running
check_processes() {
    ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | wc -l | tr -d ' '
}

# Get all PIDs for Googol components
PIDS=$(ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "✅ No Googol processes running."
    exit 0
fi

echo "Found the following processes:"
ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | while read line; do
    pid=$(echo "$line" | awk '{print $2}')
    if echo "$line" | grep -q "spring-boot:run"; then
        echo "  - Spring Boot Web Application (PID $pid)"
    elif echo "$line" | grep -q "exec:java@gateway"; then
        echo "  - Gateway (PID $pid)"
    elif echo "$line" | grep -q "exec:java@barrel"; then
        echo "  - Storage Barrel (PID $pid)"
    elif echo "$line" | grep -q "exec:java@downloader"; then
        echo "  - Downloader (PID $pid)"
    fi
done

echo ""
echo "Sending termination signal (SIGTERM)..."

# Try graceful shutdown first
for PID in $PIDS; do
    kill $PID 2>/dev/null
done

# Wait for processes to terminate gracefully
echo -n "Waiting for processes to stop"
for i in {1..10}; do
    sleep 1
    echo -n "."
    REMAINING=$(check_processes)
    if [ "$REMAINING" -eq 0 ]; then
        break
    fi
done
echo ""

# Check if any processes are still running
REMAINING_PIDS=$(ps aux | grep -i "exec:java@\(gateway\|barrel\|downloader\|frontend\)\|spring-boot:run" | grep -v grep | awk '{print $2}')

if [ -n "$REMAINING_PIDS" ]; then
    echo "⚠️  Some processes didn't stop gracefully. Force killing..."
    for PID in $REMAINING_PIDS; do
        kill -9 $PID 2>/dev/null
    done
    sleep 1
fi

# Final check
FINAL_COUNT=$(check_processes)
if [ "$FINAL_COUNT" -eq 0 ]; then
    echo "✅ All Googol system components stopped successfully!"
else
    echo "❌ Warning: Some processes may still be running."
    echo "   Run 'ps aux | grep java' to check manually."
    exit 1
fi

echo ""
echo "System stopped at $(date '+%Y-%m-%d %H:%M:%S')"
