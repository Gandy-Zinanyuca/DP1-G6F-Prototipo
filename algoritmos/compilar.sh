#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
find comun/src alns/src tabu/src experimentacion/src -name '*.java' -print |
  LC_ALL=C sort | sed 's/.*/"&"/' > out/sources.txt
javac --release 17 -encoding UTF-8 -d out @out/sources.txt
java -cp out pe.pucp.paqrap.IntegracionALNSTest
java -cp out pe.pucp.paqrap.RestriccionesEstrictasTest
echo "Compilación y pruebas correctas. Clases en algoritmos/out."
