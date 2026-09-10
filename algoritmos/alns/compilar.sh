#!/usr/bin/env bash
# Compila el componente planificador de PaqRap en la carpeta out/
set -e
cd "$(dirname "$0")"
mkdir -p out
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
echo "Compilado en out/  ->  java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador <ventas.txt> ..."
