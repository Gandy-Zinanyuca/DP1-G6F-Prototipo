#!/usr/bin/env Rscript
# =============================================================================
# PaqRap · Experimentación numérica: duración hasta el colapso, Tabú vs ALNS
# =============================================================================
#
# Hipótesis (unilateral). La medida de desempeño es cuánto dura la simulación
# hasta el colapso (por defecto, días simulados); el algoritmo que dura más es
# mejor.
#
#   H0: μ_Tabú = μ_ALNS   Tabú no tiene diferencia alguna con ALNS
#   H1: μ_Tabú > μ_ALNS   Tabú tiene mejor desempeño (dura más hasta el colapso)
#
# Procedimiento
#   1. Descriptivos por algoritmo.
#   2. Normalidad de cada muestra (Shapiro-Wilk) y homogeneidad de varianzas
#      (Fligner-Killeen, robusta a la no normalidad).
#   3. Prueba principal:
#        - ambas muestras normales  -> t de Welch unilateral (no asume varianzas iguales)
#        - alguna no normal         -> Wilcoxon-Mann-Whitney unilateral
#      La otra prueba se reporta como complementaria.
#   4. Tamaño del efecto: g de Hedges y probabilidad de superioridad
#      P(Tabú > ALNS), con su correlación biserial de rangos.
#   5. Potencia alcanzada y N por grupo necesario para detectar una diferencia
#      de interés (--delta) con potencia 0.80.
#   6. Si alguna corrida terminó sin colapsar (se acabaron los datos), su
#      duración es un valor censurado: se advierte y se agrega la prueba
#      log-rank (paquete survival) como complemento.
#
# Entrada: CSV con una fila por corrida, como los que genera
#   algoritmos/alns/experimentos_colapso.sh (opción --csv-resumen del simulador).
#   Columnas mínimas: fin, <métrica> (por defecto dias_simulados).
#   Opcionales: algoritmo, semilla, escenario.
#   Cada argumento puede ser un archivo CSV o una carpeta con varios.
#
# Uso
#   Rscript analisis_colapso.R --alns <csv|carpeta> [--tabu <csv|carpeta>]
#           [--metrica dias_simulados] [--alfa 0.05] [--delta 1] [--salida carpeta]
#
#   Solo con --alns: descriptivos, normalidad, gráficos y tamaño de muestra
#   sugerido (sirve para decidir N antes de tener las corridas de Tabú).
#
# Salida (en --salida): informe_colapso.txt, resumen_descriptivo.csv y gráficos PNG.
# =============================================================================

suppressWarnings(suppressMessages({
  tiene_survival <- requireNamespace("survival", quietly = TRUE)
}))

# ----------------------------------------------------------------- argumentos
dir_script <- local({
  arg <- grep("^--file=", commandArgs(trailingOnly = FALSE), value = TRUE)
  if (length(arg) == 1) dirname(normalizePath(sub("^--file=", "", arg))) else getwd()
})

opciones <- list(
  alns    = file.path(dir_script, "..", "alns", "resultados", "experimentos", "alns"),
  tabu    = NULL,
  metrica = "dias_simulados",
  alfa    = 0.05,
  delta   = 1,
  salida  = file.path(dir_script, "resultados")
)

args <- commandArgs(trailingOnly = TRUE)
i <- 1
while (i <= length(args)) {
  clave <- sub("^--", "", args[i])
  if (!clave %in% names(opciones) || i == length(args)) {
    stop("Opción no reconocida o sin valor: ", args[i],
         "\nUso: Rscript analisis_colapso.R --alns <csv|carpeta> [--tabu <csv|carpeta>] ",
         "[--metrica dias_simulados] [--alfa 0.05] [--delta 1] [--salida carpeta]")
  }
  opciones[[clave]] <- args[i + 1]
  i <- i + 2
}
opciones$alfa  <- as.numeric(opciones$alfa)
opciones$delta <- as.numeric(opciones$delta)
dir.create(opciones$salida, recursive = TRUE, showWarnings = FALSE)

# ----------------------------------------------------------------- lectura
leer_corridas <- function(ruta, etiqueta) {
  if (is.null(ruta)) return(NULL)
  if (dir.exists(ruta)) {
    archivos <- list.files(ruta, pattern = "\\.csv$", full.names = TRUE)
  } else if (file.exists(ruta)) {
    archivos <- ruta
  } else {
    stop("No existe: ", ruta)
  }
  if (length(archivos) == 0) stop("No hay archivos CSV en ", ruta)

  tablas <- lapply(archivos, function(f) {
    t <- read.csv(f, stringsAsFactors = FALSE, check.names = FALSE)
    if (nrow(t) > 0) t$archivo <- basename(f)
    t
  })
  columnas <- Reduce(intersect, lapply(tablas, names))
  d <- do.call(rbind, lapply(tablas, function(t) t[, columnas, drop = FALSE]))

  faltan <- setdiff(c("fin", opciones$metrica), names(d))
  if (length(faltan) > 0) {
    stop(etiqueta, ": faltan columnas ", paste(faltan, collapse = ", "), " en ", ruta)
  }
  if ("escenario" %in% names(d)) {
    otras <- d$escenario != "colapso"
    if (any(otras)) {
      message(etiqueta, ": se descartan ", sum(otras), " corridas que no son del escenario de colapso")
      d <- d[!otras, , drop = FALSE]
    }
  }
  if ("semilla" %in% names(d) && anyDuplicated(d$semilla)) {
    message(etiqueta, ": semillas repetidas; se conserva la primera corrida de cada una")
    d <- d[!duplicated(d$semilla), , drop = FALSE]
  }
  configuracion <- intersect(c("mes_inicial", "sa_min", "k", "max_iteraciones",
                               "proporcion_destruccion"), names(d))
  for (col in configuracion) {
    if (length(unique(d[[col]])) > 1) {
      warning(etiqueta, ": la muestra mezcla configuraciones distintas en '", col, "' (",
              paste(sort(unique(d[[col]])), collapse = ", "), "); las corridas no son comparables",
              call. = FALSE, immediate. = TRUE)
    }
  }
  d$algoritmo <- etiqueta
  d$duracion  <- as.numeric(d[[opciones$metrica]])
  d$colapso   <- d$fin == "COLAPSO"
  d[, c("algoritmo", "duracion", "colapso",
        intersect(c("semilla", "instante_final", "archivo"), names(d)))]
}

alns <- leer_corridas(opciones$alns, "ALNS")
tabu <- leer_corridas(opciones$tabu, "Tabu")
datos <- rbind(alns, if (!is.null(tabu)) tabu[, names(alns)])
datos$algoritmo <- factor(datos$algoritmo, levels = c("ALNS", "Tabu")[c(TRUE, !is.null(tabu))])

# ----------------------------------------------------------------- utilidades
fmt <- function(x, d = 3) formatC(x, format = "f", digits = d)

# Ruta relativa a la raíz del repositorio, para que el informe no dependa del equipo local.
raiz_repo <- normalizePath(file.path(dir_script, "..", ".."), mustWork = FALSE)
ruta_legible <- function(p) {
  np <- normalizePath(p, mustWork = FALSE)
  if (startsWith(np, paste0(raiz_repo, .Platform$file.sep))) substring(np, nchar(raiz_repo) + 2) else p
}

descriptivos <- function(x) {
  c(n = length(x), media = mean(x), de = sd(x), cv = sd(x) / mean(x),
    min = min(x), q1 = unname(quantile(x, 0.25)), mediana = median(x),
    q3 = unname(quantile(x, 0.75)), max = max(x))
}

shapiro_p <- function(x) {
  if (length(x) < 3 || length(x) > 5000 || sd(x) == 0) return(NA_real_)
  shapiro.test(x)$p.value
}

hedges_g <- function(x, y) {
  nx <- length(x); ny <- length(y)
  sp <- sqrt(((nx - 1) * var(x) + (ny - 1) * var(y)) / (nx + ny - 2))
  if (sp == 0) return(NA_real_)
  (mean(x) - mean(y)) / sp * (1 - 3 / (4 * (nx + ny) - 9))
}

magnitud_d <- function(g) {
  a <- abs(g)
  if (is.na(a)) "indefinida" else if (a < 0.2) "despreciable" else if (a < 0.5) "pequeña"
  else if (a < 0.8) "mediana" else "grande"
}

n_necesario <- function(delta, de, alfa, potencia = 0.80) {
  if (is.na(de) || de == 0) return(NA_real_)
  ceiling(power.t.test(delta = delta, sd = de, sig.level = alfa, power = potencia,
                       type = "two.sample", alternative = "one.sided")$n)
}

# ----------------------------------------------------------------- informe
archivo_informe <- file.path(opciones$salida, "informe_colapso.txt")
sink(archivo_informe, split = TRUE)

cat("=============================================================================\n")
cat(" PaqRap · Duración hasta el colapso: Tabú vs ALNS\n")
cat("=============================================================================\n")
cat(" Fecha        :", format(Sys.time(), "%Y-%m-%d %H:%M"), "\n")
cat(" Métrica      :", opciones$metrica, "(mayor es mejor)\n")
cat(" Nivel alfa   :", opciones$alfa, "\n")
cat(" ALNS         :", ruta_legible(opciones$alns), "\n")
cat(" Tabú         :", if (is.null(opciones$tabu)) "(sin datos todavía)" else ruta_legible(opciones$tabu), "\n\n")
cat(" H0: μ_Tabú = μ_ALNS  (no hay diferencia)\n")
cat(" H1: μ_Tabú > μ_ALNS  (Tabú dura más hasta el colapso)\n\n")

# --- 1. Descriptivos
cat("-- 1. Estadística descriptiva -----------------------------------------------\n")
tabla <- do.call(rbind, lapply(split(datos$duracion, datos$algoritmo), descriptivos))
print(round(tabla, 3))
write.csv(data.frame(algoritmo = rownames(tabla), tabla, row.names = NULL),
          file.path(opciones$salida, "resumen_descriptivo.csv"), row.names = FALSE)

censuradas <- tapply(!datos$colapso, datos$algoritmo, sum)
if (any(censuradas > 0)) {
  cat("\n AVISO: corridas que terminaron SIN colapsar (duración censurada, es una cota inferior):\n")
  print(censuradas[censuradas > 0])
}
for (g in levels(datos$algoritmo)) {
  x <- datos$duracion[datos$algoritmo == g]
  if (length(x) >= 2 && sd(x) == 0) {
    cat("\n AVISO:", g, "tiene varianza cero: todas las corridas duran lo mismo.",
        "Revise que las semillas cambien la búsqueda.\n")
  }
}

# --- 2. Supuestos
cat("\n-- 2. Supuestos ---------------------------------------------------------------\n")
normal <- sapply(levels(datos$algoritmo), function(g) {
  p <- shapiro_p(datos$duracion[datos$algoritmo == g])
  cat(sprintf(" Shapiro-Wilk %-5s: p = %s -> %s\n", g, ifelse(is.na(p), "NA", fmt(p, 4)),
              ifelse(is.na(p), "no evaluable (n < 3 o varianza cero)",
                     ifelse(p > opciones$alfa, "no se rechaza normalidad", "NO normal"))))
  !is.na(p) && p > opciones$alfa
})

x_alns <- datos$duracion[datos$algoritmo == "ALNS"]

if (is.null(tabu)) {
  # --- Solo ALNS: planificación del tamaño de muestra
  cat("\n-- 3. Tamaño de muestra sugerido (solo hay datos de ALNS) --------------------\n")
  de <- sd(x_alns)
  cat(sprintf(" Desviación estándar de ALNS: %s días (n = %d)\n", fmt(de), length(x_alns)))
  cat(" N por grupo para detectar que Tabú dura 'delta' días más, t unilateral,",
      "alfa =", opciones$alfa, ", potencia 0.80\n (asumiendo la misma dispersión en Tabú):\n")
  deltas <- sort(unique(c(opciones$delta, 0.25, 0.5, 1, 2, 5)))
  print(data.frame(delta_dias = deltas,
                   n_por_grupo = sapply(deltas, n_necesario, de = de, alfa = opciones$alfa)),
        row.names = FALSE)
  cat("\n Con al menos 30 corridas por grupo la prueba es robusta a desvíos moderados",
      "de normalidad.\n")
} else {
  x_tabu <- datos$duracion[datos$algoritmo == "Tabu"]

  flig <- tryCatch(fligner.test(duracion ~ algoritmo, data = datos)$p.value,
                   error = function(e) NA_real_)
  cat(sprintf(" Fligner-Killeen (varianzas iguales): p = %s\n", ifelse(is.na(flig), "NA", fmt(flig, 4))))
  cat("   (La t de Welch no requiere varianzas iguales; se reporta como información.)\n")

  # --- 3. Pruebas
  cat("\n-- 3. Prueba de hipótesis (unilateral, H1: Tabú > ALNS) ----------------------\n")
  welch <- tryCatch(t.test(x_tabu, x_alns, alternative = "greater", var.equal = FALSE,
                           conf.level = 1 - opciones$alfa), error = function(e) NULL)
  wilc <- tryCatch(suppressWarnings(
    wilcox.test(x_tabu, x_alns, alternative = "greater", exact = FALSE, correct = TRUE,
                conf.int = TRUE, conf.level = 1 - opciones$alfa)), error = function(e) NULL)

  ambas_normales <- all(normal)
  principal <- if (ambas_normales) "welch" else "wilcoxon"
  cat(" Prueba principal:", if (ambas_normales)
    "t de Welch (ambas muestras compatibles con normalidad)"
    else "Wilcoxon-Mann-Whitney (alguna muestra no es normal o no es evaluable)", "\n\n")

  if (!is.null(welch)) {
    cat(sprintf(" t de Welch       : t = %s, gl = %s, p = %s\n", fmt(welch$statistic),
                fmt(welch$parameter, 1), format.pval(welch$p.value, digits = 4)))
    cat(sprintf("   diferencia de medias (Tabú − ALNS) = %s días; IC %d%% unilateral: [%s, Inf)\n",
                fmt(mean(x_tabu) - mean(x_alns)), round(100 * (1 - opciones$alfa)),
                fmt(welch$conf.int[1])))
  } else {
    cat(" t de Welch       : no calculable (datos constantes)\n")
  }
  if (!is.null(wilc)) {
    cat(sprintf(" Wilcoxon-M-W     : W = %s, p = %s\n", fmt(wilc$statistic, 1),
                format.pval(wilc$p.value, digits = 4)))
    if (!is.null(wilc$estimate)) {
      cat(sprintf("   desplazamiento de Hodges-Lehmann = %s días; IC %d%% unilateral: [%s, Inf)\n",
                  fmt(wilc$estimate), round(100 * (1 - opciones$alfa)), fmt(wilc$conf.int[1])))
    }
  } else {
    cat(" Wilcoxon-M-W     : no calculable\n")
  }

  # --- 4. Tamaño del efecto
  cat("\n-- 4. Tamaño del efecto ------------------------------------------------------\n")
  g <- hedges_g(x_tabu, x_alns)
  ps <- mean(outer(x_tabu, x_alns, ">")) + 0.5 * mean(outer(x_tabu, x_alns, "=="))
  cat(sprintf(" g de Hedges                   = %s (%s)\n", fmt(g), magnitud_d(g)))
  cat(sprintf(" P(Tabú dura más que ALNS)     = %s\n", fmt(ps)))
  cat(sprintf(" Correlación biserial de rangos = %s\n", fmt(2 * ps - 1)))

  # --- 5. Potencia
  cat("\n-- 5. Potencia -----------------------------------------------------------------\n")
  sp <- sqrt(((length(x_tabu) - 1) * var(x_tabu) + (length(x_alns) - 1) * var(x_alns)) /
               (length(x_tabu) + length(x_alns) - 2))
  dif <- mean(x_tabu) - mean(x_alns)
  if (!is.na(sp) && sp > 0 && dif > 0) {
    pot <- power.t.test(n = min(length(x_tabu), length(x_alns)), delta = dif, sd = sp,
                        sig.level = opciones$alfa, type = "two.sample",
                        alternative = "one.sided")$power
    cat(sprintf(" Potencia para la diferencia observada (%s días): %s\n", fmt(dif), fmt(pot)))
    cat(sprintf(" N por grupo para potencia 0.80 con esa diferencia: %s\n",
                n_necesario(dif, sp, opciones$alfa)))
  } else {
    cat(" No aplica: la diferencia observada no favorece a Tabú o la dispersión es cero.\n")
  }
  cat(sprintf(" N por grupo para detectar delta = %s días (potencia 0.80): %s\n",
              opciones$delta, n_necesario(opciones$delta, sp, opciones$alfa)))

  # --- 6. Censura
  if (any(censuradas > 0)) {
    cat("\n-- 6. Corridas censuradas: prueba log-rank (complementaria, bilateral) -------\n")
    if (tiene_survival) {
      lr <- survival::survdiff(survival::Surv(duracion, colapso) ~ algoritmo, data = datos)
      p_lr <- 1 - pchisq(lr$chisq, df = length(lr$n) - 1)
      cat(sprintf(" Log-rank: chi2 = %s, p = %s\n", fmt(lr$chisq), format.pval(p_lr, digits = 4)))
    } else {
      cat(" Instale el paquete 'survival' para la prueba log-rank.\n")
    }
  }

  # --- Decisión
  prueba <- if (principal == "welch") welch else wilc
  cat("\n-- Decisión ------------------------------------------------------------------\n")
  if (is.null(prueba)) {
    cat(" No se pudo calcular la prueba principal.\n")
  } else if (prueba$p.value < opciones$alfa) {
    cat(sprintf(" p = %s < %s -> SE RECHAZA H0.\n", format.pval(prueba$p.value, digits = 4), opciones$alfa))
    cat(" Hay evidencia estadística de que Tabú dura más hasta el colapso que ALNS.\n")
  } else {
    cat(sprintf(" p = %s >= %s -> NO SE RECHAZA H0.\n", format.pval(prueba$p.value, digits = 4), opciones$alfa))
    cat(" No hay evidencia suficiente de que Tabú dure más hasta el colapso que ALNS.\n")
    if (mean(x_tabu) < mean(x_alns)) {
      cat(" Nota: en la muestra ALNS dura más que Tabú; la prueba unilateral planteada no",
          "evalúa esa dirección.\n")
    }
  }
}
sink()

# ----------------------------------------------------------------- gráficos
colores <- c(ALNS = "#1f77b4", Tabu = "#d62728")[levels(datos$algoritmo)]
etiqueta_y <- paste0(opciones$metrica, " (hasta el colapso)")

png(file.path(opciones$salida, "boxplot_colapso.png"), width = 900, height = 650, res = 120)
# outline = FALSE evita dibujar los atípicos dos veces (ya van como puntos), pero el eje Y debe
# abarcar todas las corridas para que ningún punto ni la media queden fuera del gráfico.
boxplot(duracion ~ algoritmo, data = datos, col = adjustcolor(colores, 0.35), border = colores,
        ylab = etiqueta_y, xlab = "", main = "Duración hasta el colapso por algoritmo", outline = FALSE,
        ylim = range(datos$duracion), names = levels(datos$algoritmo))
axis(1, at = seq_along(levels(datos$algoritmo)), labels = levels(datos$algoritmo))
stripchart(duracion ~ algoritmo, data = datos, vertical = TRUE, method = "jitter", pch = 19,
           col = adjustcolor(colores, 0.7), add = TRUE)
points(seq_along(levels(datos$algoritmo)), tapply(datos$duracion, datos$algoritmo, mean),
       pch = 23, bg = "white", cex = 1.4)
legend("topleft", legend = "media", pch = 23, pt.bg = "white", bty = "n")
invisible(dev.off())

png(file.path(opciones$salida, "supervivencia_colapso.png"), width = 900, height = 650, res = 120)
rango <- range(datos$duracion)
plot(NA, xlim = rango, ylim = c(0, 1), xlab = etiqueta_y, ylab = "Fracción de corridas sin colapsar",
     main = "Curva de supervivencia hasta el colapso")
for (g in levels(datos$algoritmo)) {
  x <- sort(datos$duracion[datos$algoritmo == g])
  lines(c(rango[1], x), c(1, 1 - seq_along(x) / length(x)), type = "s", col = colores[g], lwd = 2)
}
legend("topright", legend = levels(datos$algoritmo), col = colores, lwd = 2, bty = "n")
invisible(dev.off())

png(file.path(opciones$salida, "qqplot_colapso.png"), width = 450 * nlevels(datos$algoritmo),
    height = 450, res = 110)
par(mfrow = c(1, nlevels(datos$algoritmo)))
for (g in levels(datos$algoritmo)) {
  x <- datos$duracion[datos$algoritmo == g]
  qqnorm(x, main = paste("Q-Q normal:", g), col = colores[g], pch = 19)
  if (length(x) > 1 && sd(x) > 0) qqline(x)
}
invisible(dev.off())

cat("\nArchivos generados en", ruta_legible(opciones$salida), ":\n")
cat(paste0("  ", list.files(opciones$salida), collapse = "\n"), "\n")
