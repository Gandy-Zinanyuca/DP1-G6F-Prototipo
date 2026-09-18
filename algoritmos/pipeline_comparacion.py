#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pipeline de comparacion TS vs ALNS - fitness = tiempo/ciclos sostenidos antes del colapso.

Corre SimuladorColapso (Tabu Search) y DemoPlanificador (ALNS) sobre las mismas K instancias
(un mes distinto cada una, SIEMPRE arrancando en dia 1, hora 0 - ver nota de comparabilidad
abajo), junta el resultado en un CSV pareado por instancia y, si scipy esta disponible, corre
la prueba de Wilcoxon pareada sobre el fitness elegido.

REGLA DE COMPARABILIDAD (no negociable): ambos algoritmos deben arrancar en dia=1, hora=0 del
mes. ALNS considera "pedidos pendientes registrados hasta T" SIN cota inferior (ISA 5.1); si se
arranca a mitad de mes arrastra todo el backlog de los dias previos de una sola vez y colapsa
artificialmente en el primer ciclo. Arrancando en dia 1 no hay backlog previo posible y ambos
algoritmos ven, en la practica, la misma ventana de pedidos.

Uso:
    python pipeline_comparacion.py --meses 202601,202602,202603,...,202612 \
        --sa 30 --k 4 --iteraciones 100 --ciclos 150 --out resultados.csv

Requiere: java (JDK 17+) en PATH o --java <ruta>; los .class ya compilados en
algoritmos/tabu/target/classes y algoritmos/alns/out (ver README de cada modulo para compilar).
"""
import argparse
import csv
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
TABU_CLASSES = RAIZ / "tabu" / "target" / "classes"
TABU_DATOS = RAIZ / "tabu" / "datos"
ALNS_CLASSES = RAIZ / "alns" / "out"
ALNS_DATOS = RAIZ / "alns" / "data"

FITNESS_RE = re.compile(
    r"FITNESS.*?:\s*(\d+)\s*ciclos\s*=\s*(\d+)\s*min\s*=\s*([\d.]+)\s*h", re.IGNORECASE)
RESUMEN_RE = re.compile(
    r"Entregados:\s*(\d+)\s*\|\s*Distancia:\s*([\d.]+)\s*km\s*\|\s*Costo:\s*S/\s*([\d.]+)\s*\|\s*(\w[\w\s()]*)")


def parsear_salida(texto):
    m1 = FITNESS_RE.search(texto)
    m2 = RESUMEN_RE.search(texto)
    if not m1 or not m2:
        return None
    return {
        "ciclos": int(m1.group(1)),
        "minutos": int(m1.group(2)),
        "horas": float(m1.group(3)),
        "entregados": int(m2.group(1)),
        "km": float(m2.group(2)),
        "costo": float(m2.group(3)),
        "colapso": "COLAPSO" in m2.group(4) and "SIN COLAPSO" not in m2.group(4),
    }


def correr(java, args, timeout):
    proc = subprocess.run([java, "-Dfile.encoding=UTF-8", *args],
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace", timeout=timeout)
    return proc.stdout + "\n" + proc.stderr


def correr_tabu(java, anio, mes, sa, k, iteraciones, tenencia, ciclos, timeout):
    args = ["-cp", str(TABU_CLASSES), "pe.logistica.SimuladorColapso",
            "--datos", str(TABU_DATOS), "--anio", str(anio), "--mes", str(mes),
            "--dia", "1", "--hora", "0", "--sa", str(sa), "--k", str(k),
            "--iteraciones", str(iteraciones), "--tenencia", str(tenencia), "--ciclos", str(ciclos)]
    return correr(java, args, timeout)


def correr_alns(java, anio, mes, sa, k, iteraciones, semilla, ciclos, timeout):
    ventas = ALNS_DATOS / "ventas.v20260909" / f"ventas.{anio}{mes:02d}.txt"
    bloqueos = ALNS_DATOS / "bloqueos.v20260909" / f"bloqueo.{str(anio)[2:]}{mes:02d}.txt"
    mant = ALNS_DATOS / "mant.preventivo.09.10.txt"
    args = ["-cp", str(ALNS_CLASSES), "pe.pucp.paqrap.DemoPlanificador",
            str(ventas), str(bloqueos), str(mant),
            "--dia", "1", "--hora", "0", "--ciclos", str(ciclos),
            "--sa", str(sa), "--k", str(k), "--iteraciones", str(iteraciones), "--semilla", str(semilla)]
    return correr(java, args, timeout)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--meses", required=True,
                     help="Lista de instancias AAAAMM separadas por coma, ej: 202601,202602,202603")
    ap.add_argument("--sa", type=int, default=30)
    ap.add_argument("--k", type=int, default=4)
    ap.add_argument("--iteraciones", type=int, default=100)
    ap.add_argument("--tenencia", type=int, default=7, help="Solo TS")
    ap.add_argument("--semillas-alns", default="1", help="Semillas de ALNS por instancia, separadas por coma")
    ap.add_argument("--ciclos", type=int, default=150, help="Tope de ciclos por corrida")
    ap.add_argument("--timeout", type=int, default=1800, help="Segundos maximos por corrida")
    ap.add_argument("--java", default="java")
    ap.add_argument("--out", default="resultados.csv")
    ap.add_argument("--guardar-logs", action="store_true", help="Guarda la salida completa de cada corrida en logs/")
    args = ap.parse_args()

    instancias = [(int(m[:4]), int(m[4:6])) for m in args.meses.split(",")]
    semillas = [int(s) for s in args.semillas_alns.split(",")]

    if args.guardar_logs:
        (RAIZ / "logs").mkdir(exist_ok=True)

    filas = []
    for anio, mes in instancias:
        etiqueta = f"{anio}{mes:02d}"
        print(f"=== Instancia {etiqueta} ===", file=sys.stderr)

        print("  TS...", file=sys.stderr)
        salida_ts = correr_tabu(args.java, anio, mes, args.sa, args.k, args.iteraciones,
                                 args.tenencia, args.ciclos, args.timeout)
        r_ts = parsear_salida(salida_ts)
        if args.guardar_logs:
            (RAIZ / "logs" / f"ts_{etiqueta}.txt").write_text(salida_ts, encoding="utf-8")
        if r_ts is None:
            print(f"  [!] TS no produjo una linea FITNESS parseable en {etiqueta}", file=sys.stderr)
            print(salida_ts[-2000:], file=sys.stderr)
        else:
            filas.append({"instancia": etiqueta, "algoritmo": "TS", "semilla": "", **r_ts})

        for semilla in semillas:
            print(f"  ALNS semilla={semilla}...", file=sys.stderr)
            salida_alns = correr_alns(args.java, anio, mes, args.sa, args.k, args.iteraciones,
                                       semilla, args.ciclos, args.timeout)
            r_alns = parsear_salida(salida_alns)
            if args.guardar_logs:
                (RAIZ / "logs" / f"alns_{etiqueta}_s{semilla}.txt").write_text(salida_alns, encoding="utf-8")
            if r_alns is None:
                print(f"  [!] ALNS no produjo una linea FITNESS parseable en {etiqueta} (semilla {semilla})",
                      file=sys.stderr)
                print(salida_alns[-2000:], file=sys.stderr)
            else:
                filas.append({"instancia": etiqueta, "algoritmo": "ALNS", "semilla": semilla, **r_alns})

    campos = ["instancia", "algoritmo", "semilla", "ciclos", "minutos", "horas",
              "entregados", "km", "costo", "colapso"]
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=campos)
        w.writeheader()
        w.writerows(filas)
    print(f"\nCSV escrito: {args.out} ({len(filas)} filas)", file=sys.stderr)

    resumen_wilcoxon(filas, semillas)


def resumen_wilcoxon(filas, semillas):
    """Si hay >=2 instancias con TS y ALNS, arma el vector pareado (ALNS promediado por
    instancia si hubo varias semillas) e intenta correr Wilcoxon (requiere scipy)."""
    from collections import defaultdict
    ts_por_instancia = {}
    alns_por_instancia = defaultdict(list)
    for fila in filas:
        if fila["algoritmo"] == "TS":
            ts_por_instancia[fila["instancia"]] = fila["horas"]
        else:
            alns_por_instancia[fila["instancia"]].append(fila["horas"])

    comunes = sorted(set(ts_por_instancia) & set(alns_por_instancia))
    if len(comunes) < 2:
        print("\n(Menos de 2 instancias con ambos algoritmos; no se corre la prueba.)", file=sys.stderr)
        return

    ts_horas = [ts_por_instancia[i] for i in comunes]
    alns_horas = [sum(alns_por_instancia[i]) / len(alns_por_instancia[i]) for i in comunes]

    print("\n--- Fitness pareado (horas sostenidas antes de colapso) ---", file=sys.stderr)
    print(f"{'instancia':>10} {'TS':>10} {'ALNS':>10}", file=sys.stderr)
    for i, a, b in zip(comunes, ts_horas, alns_horas):
        print(f"{i:>10} {a:>10.2f} {b:>10.2f}", file=sys.stderr)

    try:
        from scipy.stats import wilcoxon
        estad, p = wilcoxon(ts_horas, alns_horas)
        print(f"\nWilcoxon (dos colas) TS vs ALNS: estadistico={estad:.3f}  p={p:.4f}", file=sys.stderr)
        print("(usa alternative='less'/'greater' en tu propio analisis si tu H1 es direccional; "
              "decide la direccion ANTES de mirar el resultado)", file=sys.stderr)
    except ImportError:
        print("\n(scipy no esta instalado; instala con 'pip install scipy' para correr Wilcoxon "
              "automaticamente, o usa el CSV con R: wilcox.test(ts, alns, paired=TRUE))", file=sys.stderr)


if __name__ == "__main__":
    main()
