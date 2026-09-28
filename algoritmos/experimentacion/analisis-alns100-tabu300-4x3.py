#!/usr/bin/env python3
"""Analisis pareado del diseno 4 fechas x 3 semillas x TS/ALNS.

La unidad experimental es el bloque (fecha, semilla). Los ciclos internos no se
tratan como replicas. La prueba numerica es una permutacion pareada bilateral de
las etiquetas TS/ALNS; con 12 pares se enumeran exactamente las 2^12 asignaciones.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path
import sys

import numpy as np
import pandas as pd

FECHAS = ("2026-02", "2026-08", "2027-03", "2027-06")
SEMILLAS = (20262, 20263, 20264)
ALGORITMOS = ("TS", "ALNS")
METRICAS = (
    ("Ta_promedio_ms", "Ta promedio por planificacion (ms)", True),
    ("Ta_mediana_ms", "Ta mediana por planificacion (ms)", True),
    ("Ta_p90_ms", "Ta P90 por planificacion (ms)", True),
    ("Ta_total_ms", "Ta acumulado de la corrida (ms)", True),
    ("tiempo_simulacion_real_ms", "Tiempo real de simulacion Java (ms)", True),
    ("tiempo_proceso_real_ms", "Tiempo real del proceso completo (ms)", True),
    ("holgura_real_promedio_min", "Holgura real promedio (min)", False),
    ("holgura_real_minima_min", "Holgura real minima (min)", False),
    ("duracion_dias", "Duracion simulada (dias)", False),
    ("pedidos_completos", "Pedidos completados", False),
    ("paquetes_entregados", "Paquetes entregados", False),
    ("distancia_despachada_km", "Distancia despachada (km)", False),
    ("tiempo_rutas_despachadas_min", "Tiempo de rutas (min)", False),
    ("vehiculos_utilizados", "Vehiculos utilizados", False),
    ("utilizacion_capacidad", "Utilizacion de capacidad", False),
)


def argumentos() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Analisis pareado de la campana oficial TS vs ALNS")
    p.add_argument("--entrada", required=True, help="Carpeta de campana que contiene corridas/")
    p.add_argument("--salida", help="Carpeta de analisis; por defecto <entrada>/analisis")
    p.add_argument("--permitir-incompleta", action="store_true",
                   help="Analiza solo pares completos, dejando constancia de faltantes")
    p.add_argument("--self-test", action="store_true", help=argparse.SUPPRESS)
    return p.parse_args()


def fecha_desde_ruta(archivo: Path) -> str:
    for parte in archivo.parts:
        if len(parte) == 6 and parte.isdigit():
            candidata = f"{parte[:4]}-{parte[4:]}"
            if candidata in FECHAS:
                return candidata
    raise ValueError(f"No se pudo deducir la fecha experimental de {archivo}")


def cargar(entrada: Path) -> pd.DataFrame:
    archivos = sorted((entrada / "corridas").glob("**/resumen.csv"))
    if not archivos:
        raise SystemExit(f"No hay resumen.csv bajo {entrada / 'corridas'}")
    filas = []
    for archivo in archivos:
        tabla = pd.read_csv(archivo)
        if len(tabla) != 1:
            raise SystemExit(f"Se esperaba una fila en {archivo}; se encontraron {len(tabla)}")
        fila = tabla.iloc[0].to_dict()
        fila["fecha"] = fecha_desde_ruta(archivo)
        fila["archivo"] = str(archivo)
        filas.append(fila)
    datos = pd.DataFrame(filas)
    claves = ["fecha", "semilla", "algoritmo"]
    duplicadas = datos.duplicated(claves, keep=False)
    if duplicadas.any():
        raise SystemExit("Hay corridas duplicadas:\n" + datos.loc[duplicadas, claves + ["archivo"]].to_string(index=False))

    manifiesto = entrada / "corridas.csv"
    if manifiesto.exists():
        m = pd.read_csv(manifiesto)
        m = m.sort_values("fin_real").drop_duplicates(claves, keep="last")
        datos = datos.merge(m[[*claves, "orden_en_bloque", "tiempo_proceso_real_ms"]], on=claves, how="left")
    else:
        datos["orden_en_bloque"] = np.nan
        datos["tiempo_proceso_real_ms"] = np.nan
    return datos


def validar_diseno(datos: pd.DataFrame, permitir_incompleta: bool) -> list[tuple[str, int, str]]:
    esperadas = {(f, s, a) for f in FECHAS for s in SEMILLAS for a in ALGORITMOS}
    observadas = set(zip(datos.fecha.astype(str), datos.semilla.astype(int), datos.algoritmo.astype(str)))
    extras = sorted(observadas - esperadas)
    faltantes = sorted(esperadas - observadas)
    if extras:
        raise SystemExit(f"La carpeta mezcla {len(extras)} corridas fuera del diseno: {extras}")
    if faltantes and not permitir_incompleta:
        raise SystemExit(f"Campana incompleta: faltan {len(faltantes)} de 24 corridas. "
                         "Use --permitir-incompleta solo para un informe provisional.\n" + str(faltantes))
    return faltantes


def pares(datos: pd.DataFrame, metrica: str) -> pd.DataFrame:
    base = datos[["fecha", "semilla", "algoritmo", metrica]].copy()
    base[metrica] = pd.to_numeric(base[metrica], errors="coerce")
    p = base.pivot(index=["fecha", "semilla"], columns="algoritmo", values=metrica).dropna()
    if not {"TS", "ALNS"}.issubset(p.columns):
        return pd.DataFrame(columns=["TS", "ALNS", "diferencia_TS_menos_ALNS"])
    p["diferencia_TS_menos_ALNS"] = p.TS - p.ALNS
    return p.reset_index()


def p_permutacion_pareada(diferencias: np.ndarray) -> float:
    d = np.asarray(diferencias, dtype=float)
    d = d[np.isfinite(d)]
    if not len(d):
        return math.nan
    observado = abs(float(np.mean(d)))
    if observado == 0 and np.all(d == 0):
        return 1.0
    total = 1 << len(d)
    extremos = 0
    # Con el diseno oficial n=20: 1 048 576 combinaciones, calculo exacto.
    if len(d) <= 22:
        for inicio in range(0, total, 65_536):
            bits = np.arange(inicio, min(inicio + 65_536, total), dtype=np.uint32)
            sumas = np.zeros(len(bits), dtype=float)
            for i, valor in enumerate(d):
                sumas += np.where((bits >> i) & 1, valor, -valor)
            extremos += int(np.count_nonzero(np.abs(sumas / len(d)) >= observado - 1e-12))
        return extremos / total
    rng = np.random.default_rng(20260928)
    muestras = 200_000
    extremos = 0
    for _ in range(muestras):
        extremos += abs(float(np.mean(d * rng.choice((-1, 1), size=len(d))))) >= observado
    return (extremos + 1) / (muestras + 1)


def intervalo_bootstrap(diferencias: np.ndarray) -> tuple[float, float]:
    d = np.asarray(diferencias, dtype=float)
    rng = np.random.default_rng(20260928)
    medias = np.mean(rng.choice(d, size=(20_000, len(d)), replace=True), axis=1)
    return tuple(np.percentile(medias, [2.5, 97.5]))


def p_binomial_bilateral(a: int, b: int) -> float:
    n = a + b
    if n == 0:
        return 1.0
    menor = min(a, b)
    cola = sum(math.comb(n, k) for k in range(menor + 1)) / (2 ** n)
    return min(1.0, 2 * cola)


def analizar(datos: pd.DataFrame, salida: Path, faltantes: list[tuple[str, int, str]]) -> None:
    salida.mkdir(parents=True, exist_ok=True)
    datos.to_csv(salida / "resultados_consolidados.csv", index=False)
    filas = []
    diferencias_todas = []
    for metrica, etiqueta, menor_mejor in METRICAS:
        if metrica not in datos.columns:
            continue
        p = pares(datos, metrica)
        if p.empty:
            continue
        d = p.diferencia_TS_menos_ALNS.to_numpy(float)
        ic_inf, ic_sup = intervalo_bootstrap(d)
        de = np.std(d, ddof=1) if len(d) > 1 else math.nan
        dz = np.mean(d) / de if de and np.isfinite(de) else math.nan
        filas.append({
            "metrica": metrica, "descripcion": etiqueta, "n_pares": len(d),
            "media_TS": p.TS.mean(), "media_ALNS": p.ALNS.mean(),
            "mediana_TS": p.TS.median(), "mediana_ALNS": p.ALNS.median(),
            "diferencia_media_TS_menos_ALNS": np.mean(d),
            "IC95_bootstrap_inferior": ic_inf, "IC95_bootstrap_superior": ic_sup,
            "p_permutacion_pareada_bilateral": p_permutacion_pareada(d),
            "d_cohen_pareado": dz,
            "direccion_favorable": ("menor" if menor_mejor else "mayor"),
        })
        p.insert(2, "metrica", metrica)
        diferencias_todas.append(p)
    resumen = pd.DataFrame(filas)
    resumen.to_csv(salida / "resumen_estadistico.csv", index=False)
    if diferencias_todas:
        pd.concat(diferencias_todas, ignore_index=True).to_csv(salida / "diferencias_pareadas.csv", index=False)

    colapso = datos.assign(colapso=datos.fin.eq("COLAPSO_PLANIFICACION")).pivot(
        index=["fecha", "semilla"], columns="algoritmo", values="colapso").dropna()
    ts_si_alns_no = int(((colapso.TS == True) & (colapso.ALNS == False)).sum())
    ts_no_alns_si = int(((colapso.TS == False) & (colapso.ALNS == True)).sum())
    p_mcnemar = p_binomial_bilateral(ts_si_alns_no, ts_no_alns_si)
    colapsos_ts = int(datos.loc[datos.algoritmo.eq("TS"), "fin"].eq("COLAPSO_PLANIFICACION").sum())
    colapsos_alns = int(datos.loc[datos.algoritmo.eq("ALNS"), "fin"].eq("COLAPSO_PLANIFICACION").sum())

    lineas = [
        "RESULTADOS DEL EXPERIMENTO PAREADO TS VS ALNS",
        "=" * 49,
        f"Corridas encontradas: {len(datos)}/24; bloques pareados completos: {len(colapso)}/12.",
        f"Faltantes: {len(faltantes)}.",
        "Unidad experimental: una fecha y una semilla; los ciclos no son replicas.",
        "Diferencias: TS - ALNS. Prueba bilateral de permutacion pareada (alfa=0.05).",
        "IC95: bootstrap pareado con semilla fija. Resultados provisionales si n<12.",
        "",
        "Colapso:",
        f"  TS: {colapsos_ts}",
        f"  ALNS: {colapsos_alns}",
        f"  Discordantes TS-si/ALNS-no={ts_si_alns_no}; TS-no/ALNS-si={ts_no_alns_si}; p exacta={p_mcnemar:.6g}",
        "",
        "Metricas:",
    ]
    for fila in filas:
        decision = "diferencia detectable" if fila["p_permutacion_pareada_bilateral"] < .05 else "sin evidencia suficiente"
        lineas.append(
            f"  {fila['metrica']}: TS={fila['media_TS']:.3f}; ALNS={fila['media_ALNS']:.3f}; "
            f"dif={fila['diferencia_media_TS_menos_ALNS']:.3f}; "
            f"IC95=[{fila['IC95_bootstrap_inferior']:.3f}, {fila['IC95_bootstrap_superior']:.3f}]; "
            f"p={fila['p_permutacion_pareada_bilateral']:.6g} ({decision}).")
    if faltantes:
        lineas += ["", "ADVERTENCIA: informe provisional; faltan corridas del diseno."]
    (salida / "informe_experimento.txt").write_text("\n".join(lineas) + "\n", encoding="utf-8")
    print("\n".join(lineas))


def self_test() -> None:
    assert p_permutacion_pareada(np.array([0.0, 0.0])) == 1.0
    assert p_binomial_bilateral(0, 4) == 0.125
    assert p_permutacion_pareada(np.array([1.0, 1.0, 1.0, 1.0])) == 0.125
    print("Self-test estadistico correcto.")


def main() -> int:
    args = argumentos()
    if args.self_test:
        self_test()
        return 0
    entrada = Path(args.entrada)
    salida = Path(args.salida) if args.salida else entrada / "analisis"
    datos = cargar(entrada)
    faltantes = validar_diseno(datos, args.permitir_incompleta)
    analizar(datos, salida, faltantes)
    print(f"\nArchivos generados en {salida.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
