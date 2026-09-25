#!/usr/bin/env python3
# =============================================================================
# PaqRap - Experimentacion numerica: TS vs ALNS, comparacion PAREADA por semilla
# =============================================================================
#
# Adaptacion a Python (scipy/pandas/statsmodels) del analisis historico en R
# (analisis_colapso.R), pero para el formato nuevo y simetrico que produce
# SimulacionComparada: cada semilla corre TS y ALNS sobre EXACTAMENTE la misma
# instancia (mismos pedidos, bloqueos, mantenimiento y averias generadas para
# esa semilla). Eso hace que el diseno sea pareado (una observacion de TS y una
# de ALNS por semilla), no de dos muestras independientes como el script viejo.
#
# Hipotesis (por defecto, bilateral; no se asume de antemano cual algoritmo es
# mejor, a diferencia del script R historico que asumia H1: Tabu > ALNS):
#   H0: la mediana/media de las diferencias (TS - ALNS) es 0
#   H1: la diferencia (TS - ALNS) es distinta de 0 (o mayor/menor con --alternativa)
#
# Procedimiento
#   1. Descriptivos por algoritmo y de las diferencias pareadas.
#   2. Normalidad de las diferencias (Shapiro-Wilk).
#   3. Prueba principal sobre las diferencias pareadas:
#        - diferencias compatibles con normalidad -> t pareada (ttest_rel)
#        - si no                                  -> Wilcoxon de rangos con signo
#      La otra se reporta como complementaria.
#   4. Tamano del efecto: d de Cohen pareado (dz) y P(TS > ALNS) (proporcion de
#      diferencias positivas, con empates repartidos a la mitad).
#   5. Potencia alcanzada y N de semillas necesario para detectar --delta dias
#      de diferencia con potencia 0.80 (prueba t de una muestra sobre las
#      diferencias, que es equivalente a la t pareada).
#   6. Corridas que NO colapsaron (fin != COLAPSO_PLANIFICACION) tienen una
#      duracion CENSURADA (cota inferior, no el tiempo real hasta el fallo): se
#      excluyen de la prueba principal por defecto y se listan aparte. Use
#      --incluir-censuradas para forzar incluirlas (con esa advertencia).
#
# Entrada: uno o mas resumen.csv generados por SimulacionComparada (o carpetas
# que los contengan). Columnas requeridas: algoritmo, semilla, factor_carga,
# fin, <metrica> (por defecto duracion_dias).
#
# Uso
#   python analisis_colapso_pareado.py --entrada resultados/campana-colapso ...
#          [--metrica duracion_dias] [--alfa 0.05] [--delta 1]
#          [--alternativa two-sided|greater|less] [--incluir-censuradas]
#          [--salida resultados/analisis-colapso-pareado]
#
# Salida (en --salida): informe_colapso.txt, resumen_descriptivo.csv,
# diferencias_pareadas.csv, boxplot_pareado.png, qqplot_diferencias.png.
# =============================================================================
import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from scipy import stats
from statsmodels.stats.power import TTestPower

COLAPSO = "COLAPSO_PLANIFICACION"


def leer_resumenes(rutas):
    archivos = []
    for r in rutas:
        p = Path(r)
        if p.is_dir():
            archivos += sorted(p.glob("**/resumen.csv"))
        elif p.is_file():
            archivos.append(p)
        else:
            raise SystemExit(f"No existe: {r}")
    if not archivos:
        raise SystemExit("No se encontro ningun resumen.csv en las rutas indicadas")
    tablas = []
    for f in archivos:
        t = pd.read_csv(f)
        t["archivo"] = str(f)
        tablas.append(t)
    d = pd.concat(tablas, ignore_index=True)
    faltan = {"algoritmo", "semilla", "fin"} - set(d.columns)
    if faltan:
        raise SystemExit(f"Faltan columnas {faltan} en los resumen.csv de entrada")
    return d


def emparejar(d, metrica, factor_carga):
    d = d[d["factor_carga"] == factor_carga] if "factor_carga" in d.columns else d
    algos = sorted(d["algoritmo"].unique())
    if len(algos) != 2:
        raise SystemExit(f"Se necesitan exactamente 2 algoritmos para parear; se encontraron: {algos}")
    a, b = algos
    pivote = d.pivot_table(index="semilla", columns="algoritmo",
                            values=[metrica, "fin"], aggfunc="first")
    incompletos = pivote[metrica].isna().any(axis=1)
    if incompletos.any():
        print(f"AVISO: {incompletos.sum()} semilla(s) sin corrida de ambos algoritmos; se descartan: "
              f"{list(pivote.index[incompletos])}")
    pivote = pivote[~incompletos]
    if pivote.empty:
        raise SystemExit("Ninguna semilla tiene corrida pareada de los dos algoritmos")
    pares = pd.DataFrame({
        "semilla": pivote.index,
        a: pivote[metrica][a].values,
        b: pivote[metrica][b].values,
        f"fin_{a}": pivote["fin"][a].values,
        f"fin_{b}": pivote["fin"][b].values,
    })
    pares["colapso_ambos"] = (pares[f"fin_{a}"] == COLAPSO) & (pares[f"fin_{b}"] == COLAPSO)
    return a, b, pares


def descriptivos(x):
    x = np.asarray(x, dtype=float)
    return {
        "n": len(x), "media": np.mean(x), "de": np.std(x, ddof=1) if len(x) > 1 else np.nan,
        "cv": (np.std(x, ddof=1) / np.mean(x)) if len(x) > 1 and np.mean(x) != 0 else np.nan,
        "min": np.min(x), "q1": np.percentile(x, 25), "mediana": np.median(x),
        "q3": np.percentile(x, 75), "max": np.max(x),
    }


def shapiro_p(x):
    x = np.asarray(x, dtype=float)
    if len(x) < 3 or len(x) > 5000 or np.std(x) == 0:
        return np.nan
    return stats.shapiro(x).pvalue


def magnitud_d(d):
    if np.isnan(d):
        return "indefinida"
    a = abs(d)
    if a < 0.2:
        return "despreciable"
    if a < 0.5:
        return "pequena"
    if a < 0.8:
        return "mediana"
    return "grande"


def n_necesario(delta, de, alfa, alternativa, potencia=0.80):
    if de is None or np.isnan(de) or de == 0 or delta == 0:
        return np.nan
    dz = abs(delta) / de
    tipo = "two-sided" if alternativa == "two-sided" else "smaller" if alternativa == "less" else "larger"
    try:
        n = TTestPower().solve_power(effect_size=dz, alpha=alfa, power=potencia, alternative=tipo)
        return int(np.ceil(n))
    except Exception:
        return np.nan


def main():
    ap = argparse.ArgumentParser(description="Comparacion pareada TS vs ALNS (duracion hasta el colapso u otra metrica)")
    ap.add_argument("--entrada", nargs="+", required=True, help="Uno o mas resumen.csv o carpetas que los contengan")
    ap.add_argument("--metrica", default="duracion_dias")
    ap.add_argument("--factor-carga", type=float, default=1.0)
    ap.add_argument("--alfa", type=float, default=0.05)
    ap.add_argument("--delta", type=float, default=1.0, help="Diferencia de interes (unidades de la metrica) para calculo de potencia/N")
    ap.add_argument("--alternativa", choices=["two-sided", "greater", "less"], default="two-sided",
                     help="greater/less se interpretan sobre (primer algoritmo alfabeticamente - segundo)")
    ap.add_argument("--incluir-censuradas", action="store_true",
                     help="Incluye en la prueba principal semillas donde algun algoritmo no colapso (censura por la derecha)")
    ap.add_argument("--salida", default="resultados/analisis-colapso-pareado")
    args = ap.parse_args()

    salida = Path(args.salida)
    salida.mkdir(parents=True, exist_ok=True)

    d = leer_resumenes(args.entrada)
    a, b, pares = emparejar(d, args.metrica, args.factor_carga)

    censuradas = pares[~pares["colapso_ambos"]]
    usados = pares if args.incluir_censuradas else pares[pares["colapso_ambos"]]
    if usados.empty:
        raise SystemExit("No hay semillas utilizables para la prueba principal (todas censuradas); "
                          "use --incluir-censuradas para forzar, con precaucion.")

    pares.to_csv(salida / "diferencias_pareadas.csv", index=False)

    x_a, x_b = usados[a].to_numpy(float), usados[b].to_numpy(float)
    diff = x_a - x_b  # a - b, orden alfabetico

    informe = []

    def out(s=""):
        informe.append(s)
        print(s)

    out("=" * 79)
    out(" PaqRap - Comparacion pareada por semilla: TS vs ALNS")
    out("=" * 79)
    out(f" Fecha        : {pd.Timestamp.now():%Y-%m-%d %H:%M}")
    out(f" Metrica      : {args.metrica} (comparacion pareada por semilla)")
    out(f" Factor carga : {args.factor_carga}")
    out(f" Nivel alfa   : {args.alfa}")
    out(f" Alternativa  : {args.alternativa}")
    out(f" Entrada      : {', '.join(args.entrada)}")
    out(f" Algoritmos   : {a} vs {b} (diferencia reportada = {a} - {b})")
    out(f" Semillas pareadas totales: {len(pares)} | usadas en la prueba: {len(usados)} "
        f"({'incluye censuradas' if args.incluir_censuradas else 'excluye censuradas'})")
    out("")
    out(f" H0: la diferencia ({a} - {b}) tiene media/mediana 0")
    if args.alternativa == "two-sided":
        out(f" H1: la diferencia ({a} - {b}) es distinta de 0")
    elif args.alternativa == "greater":
        out(f" H1: {a} > {b} (diferencia > 0)")
    else:
        out(f" H1: {a} < {b} (diferencia < 0)")
    out("")

    out("-- 1. Estadistica descriptiva ------------------------------------------------")
    tabla = pd.DataFrame({a: descriptivos(x_a), b: descriptivos(x_b),
                          f"diferencia ({a}-{b})": descriptivos(diff)}).T
    out(tabla.round(3).to_string())
    tabla.round(6).to_csv(salida / "resumen_descriptivo.csv")

    if not censuradas.empty:
        out("")
        out(f" AVISO: {len(censuradas)} semilla(s) con al menos un algoritmo SIN colapsar "
            f"(duracion censurada, es una cota inferior):")
        for _, fila in censuradas.iterrows():
            out(f"   semilla {int(fila['semilla'])}: {a}={fila[f'fin_{a}']} ({fila[a]:.3f} d) | "
                f"{b}={fila[f'fin_{b}']} ({fila[b]:.3f} d)")
        if not args.incluir_censuradas:
            out(" Se excluyen de la prueba principal (comparar un colapso real contra un truncamiento no es valido).")

    if len(usados) >= 2 and np.std(diff) == 0:
        out(f"\n AVISO: la diferencia {a}-{b} es constante en todas las semillas usadas; "
            "revise si las semillas realmente cambian la busqueda/las averias.")

    out("\n-- 2. Normalidad de las diferencias pareadas ----------------------------------")
    p_norm = shapiro_p(diff)
    normal = (not np.isnan(p_norm)) and p_norm > args.alfa
    out(f" Shapiro-Wilk sobre ({a} - {b}): p = {p_norm if np.isnan(p_norm) else round(p_norm, 4)} -> "
        f"{'no evaluable (n<3 o varianza cero)' if np.isnan(p_norm) else ('no se rechaza normalidad' if normal else 'NO normal')}")

    out("\n-- 3. Prueba de hipotesis principal (pareada) ---------------------------------")
    principal = "t pareada" if normal else "Wilcoxon (rangos con signo)"
    out(f" Prueba principal: {principal} "
        f"({'diferencias compatibles con normalidad' if normal else 'diferencias no evaluables o no normales'})")

    t_res = None
    if len(usados) >= 2 and np.std(diff) > 0:
        t_res = stats.ttest_rel(x_a, x_b, alternative=args.alternativa)
        out(f" t pareada        : t = {t_res.statistic:.3f}, gl = {len(diff) - 1}, p = {t_res.pvalue:.4g}")
        out(f"   diferencia media ({a} - {b}) = {np.mean(diff):.3f} (metrica: {args.metrica})")
    else:
        out(" t pareada        : no calculable (menos de 2 semillas o diferencias constantes)")

    w_res = None
    if len(usados) >= 1 and np.any(diff != 0):
        try:
            w_res = stats.wilcoxon(x_a, x_b, alternative=args.alternativa, zero_method="wilcox")
            out(f" Wilcoxon (signo) : W = {w_res.statistic:.1f}, p = {w_res.pvalue:.4g}")
        except ValueError as e:
            out(f" Wilcoxon (signo) : no calculable ({e})")
    else:
        out(" Wilcoxon (signo) : no calculable (todas las diferencias son cero)")

    out("\n-- 4. Tamano del efecto --------------------------------------------------------")
    de_diff = np.std(diff, ddof=1) if len(diff) > 1 else np.nan
    dz = (np.mean(diff) / de_diff) if de_diff and not np.isnan(de_diff) and de_diff != 0 else np.nan
    empates = np.sum(diff == 0)
    p_sup = (np.sum(diff > 0) + 0.5 * empates) / len(diff) if len(diff) else np.nan
    out(f" d de Cohen pareado (dz)        = {dz if np.isnan(dz) else round(dz, 3)} ({magnitud_d(dz)})")
    out(f" P({a} > {b}) en la muestra      = {p_sup if np.isnan(p_sup) else round(p_sup, 3)} "
        f"({int(np.sum(diff > 0))} de {len(diff)} semillas; {empates} empate(s))")

    out("\n-- 5. Potencia -------------------------------------------------------------------")
    if de_diff and not np.isnan(de_diff) and de_diff > 0:
        tipo = "two-sided" if args.alternativa == "two-sided" else ("larger" if args.alternativa == "greater" else "smaller")
        try:
            pot_obs = TTestPower().power(effect_size=abs(np.mean(diff)) / de_diff, nobs=len(diff),
                                          alpha=args.alfa, alternative=tipo)
            out(f" Potencia alcanzada para la diferencia observada ({np.mean(diff):.3f}): {pot_obs:.3f}")
        except Exception:
            out(" Potencia alcanzada: no calculable")
        n_obs = n_necesario(np.mean(diff), de_diff, args.alfa, args.alternativa)
        out(f" N de semillas para potencia 0.80 con la diferencia observada: {n_obs}")
    else:
        out(" No aplica: dispersion de las diferencias es cero o insuficiente.")
    n_delta = n_necesario(args.delta, de_diff, args.alfa, args.alternativa)
    out(f" N de semillas para detectar delta = {args.delta} (potencia 0.80, asumiendo la dispersion observada): {n_delta}")

    out("\n-- Decision ------------------------------------------------------------------")
    prueba = t_res if normal else w_res
    if prueba is None:
        out(" No se pudo calcular la prueba principal (datos insuficientes o constantes).")
    elif prueba.pvalue < args.alfa:
        out(f" p = {prueba.pvalue:.4g} < {args.alfa} -> SE RECHAZA H0.")
        out(f" Hay evidencia estadistica de diferencia entre {a} y {b} en {args.metrica} (alternativa: {args.alternativa}).")
    else:
        out(f" p = {prueba.pvalue:.4g} >= {args.alfa} -> NO SE RECHAZA H0.")
        out(f" No hay evidencia suficiente de diferencia entre {a} y {b} en {args.metrica} bajo esta muestra.")

    (salida / "informe_colapso.txt").write_text("\n".join(informe) + "\n", encoding="utf-8")

    graficar(a, b, usados, diff, args, salida)

    print(f"\nArchivos generados en {salida}:")
    for f in sorted(salida.iterdir()):
        print(f"  {f.name}")


def graficar(a, b, usados, diff, args, salida):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, ax = plt.subplots(figsize=(7.5, 5.4), dpi=120)
    for _, fila in usados.iterrows():
        ax.plot([0, 1], [fila[a], fila[b]], color="0.75", linewidth=1, zorder=1)
    ax.scatter(np.zeros(len(usados)), usados[a], color="#1f77b4", zorder=2, label=a)
    ax.scatter(np.ones(len(usados)), usados[b], color="#d62728", zorder=2, label=b)
    ax.set_xticks([0, 1], [a, b])
    ax.set_xlim(-0.3, 1.3)
    ax.set_ylabel(args.metrica)
    ax.set_title(f"{args.metrica} por semilla (pares unidos): {a} vs {b}")
    ax.legend(loc="best")
    fig.tight_layout()
    fig.savefig(salida / "boxplot_pareado.png")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(5, 5), dpi=120)
    if len(diff) > 1 and np.std(diff) > 0:
        stats.probplot(diff, dist="norm", plot=ax)
    else:
        ax.text(0.5, 0.5, "Diferencias constantes\no muestra insuficiente", ha="center", va="center")
    ax.set_title(f"Q-Q normal de la diferencia ({a} - {b})")
    fig.tight_layout()
    fig.savefig(salida / "qqplot_diferencias.png")
    plt.close(fig)


if __name__ == "__main__":
    sys.exit(main())
