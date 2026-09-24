#!/usr/bin/env bash
# Ejecuta N simulaciones hasta el colapso con ALNS, una por semilla, en paralelo.
#
# Uso:      ./experimentos_colapso.sh N [opciones de DemoPlanificador]
# Ejemplos: ./experimentos_colapso.sh 30
#           ./experimentos_colapso.sh 30 --iteraciones 200
#           PARALELO=4 SEMILLA_BASE=1000 MES=202601 ./experimentos_colapso.sh 10
#
# Cada corrida usa la semilla SEMILLA_BASE + i (i = 1..N) y deja en resultados/experimentos/alns/:
#   alns_semilla_<s>.csv   una fila con el resumen (entrada del análisis en R)
#   alns_semilla_<s>.txt   la salida completa de la simulación
# Las semillas que ya tienen su CSV se omiten, así que el script puede relanzarse para completar
# una campaña interrumpida o ampliarla (subiendo N).
#
# Variables de entorno: PARALELO (corridas simultáneas; por defecto la mitad de los núcleos),
# SEMILLA_BASE (por defecto 0), MES (mes inicial AAAAMM, por defecto 202601),
# DESTINO (carpeta de resultados), JAVA_OPTS (por defecto -Xmx1g).
# Use un DESTINO distinto para cada configuración (p. ej. otro número de iteraciones): la
# omisión de semillas ya ejecutadas solo mira la semilla, no los parámetros.
set -euo pipefail
cd "$(dirname "$0")"

if [[ $# -lt 1 || ! "$1" =~ ^[0-9]+$ ]]; then
    sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
N=$1
shift
EXTRA=("$@")

PARALELO=${PARALELO:-$(( $(nproc) / 2 > 0 ? $(nproc) / 2 : 1 ))}
SEMILLA_BASE=${SEMILLA_BASE:-0}
MES=${MES:-202601}
JAVA_OPTS=${JAVA_OPTS:--Xmx1g}
AAMM=${MES:2:4}
VENTAS=data/ventas.v20260909/ventas.${MES}.txt
BLOQUEOS=data/bloqueos.v20260909/bloqueo.${AAMM}.txt
MANT=data/mant.preventivo.09.10.txt
DESTINO=${DESTINO:-resultados/experimentos/alns}

[[ -f "$VENTAS" ]] || { echo "No existe $VENTAS" >&2; exit 1; }

# Compila siempre: garantiza que las corridas usan el código actual.
mkdir -p out "$DESTINO"
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt

corrida() {
    local s=$1
    local csv="$DESTINO/alns_semilla_${s}.csv"
    if [[ -s "$csv" ]]; then
        echo "semilla $s: ya existe, se omite"
        return 0
    fi
    # Se escribe a un temporal y se renombra al final: un CSV presente es una corrida completa.
    local tmp="$DESTINO/.alns_semilla_${s}.csv.tmp"
    rm -f "$tmp"
    java $JAVA_OPTS -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador \
        "$VENTAS" "$BLOQUEOS" "$MANT" --colapso --dia 1 --hora 0 --sa 10 --k 7 \
        --iteraciones 200 --semilla "$s" "${EXTRA[@]}" --csv-resumen "$tmp" \
        > "$DESTINO/alns_semilla_${s}.txt" 2>&1
    mv "$tmp" "$csv"
    echo "semilla $s: $(grep -E 'COLAPSO LOG|Fin de la simul' "$DESTINO/alns_semilla_${s}.txt" | head -1 | sed 's/^ *//')"
}
export -f corrida
export DESTINO VENTAS BLOQUEOS MANT JAVA_OPTS
export EXTRA_SERIALIZADO="${EXTRA[*]:-}"

echo "ALNS hasta el colapso: N=$N, semillas $((SEMILLA_BASE + 1))..$((SEMILLA_BASE + N)), $PARALELO en paralelo, mes inicial $MES"
echo "opciones extra: ${EXTRA[*]:-(ninguna)}"
inicio=$(date +%s)
seq $((SEMILLA_BASE + 1)) $((SEMILLA_BASE + N)) \
    | xargs -P "$PARALELO" -I{} bash -c 'EXTRA=($EXTRA_SERIALIZADO); corrida "$@"' _ {}
echo "Listo en $(( $(date +%s) - inicio )) s. Resultados en $DESTINO/"
