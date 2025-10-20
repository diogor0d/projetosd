#!/bin/bash

# Script para iniciar Storage Barrel

if [ -z "$1" ]; then
    echo "Usage: ./start-barrel.sh <barrel-id>"
    exit 1
fi

BARREL_ID=$1

echo "Starting Storage Barrel $BARREL_ID..."

# Compila o código
javac -d ../bin -cp ../src ../src/common/*.java ../src/barrel/*.java

# Inicia o barrel
java -cp ../bin barrel.StorageBarrel $BARREL_ID
