# Experimento ALNS100 / Tabu300, diseño 4x3

Esta variante pertenece a la rama `integracion/alns100-tabu300`. Se mantiene separada del experimento de otro integrante que usa 300 iteraciones para ambos algoritmos, aunque los dos comparten el diseño de cuatro fechas, tres semillas y dos algoritmos.

## Diseño fijado

- Fechas iniciales: `2026-02`, `2026-08`, `2027-03` y `2027-06`.
- Semillas: `20262`, `20263` y `20264`.
- Ventana independiente por fecha: 4320 ciclos de 10 minutos, equivalentes a 30 días.
- Reinicio por fecha: flota, almacenes y operaciones comienzan desde cero.
- TS: 300 iteraciones, tenencia 7 y hasta 400 candidatos.
- ALNS: 100 iteraciones, destrucción 2, segmento 5, reacción 0.7 y temperatura 0.05.
- Presupuesto temporal: 0, porque `Ta` es una variable de respuesta.
- Ejecución secuencial y orden TS/ALNS alternado por bloque fecha-semilla.

Son 24 corridas y 12 pares. Abril de 2027 se retiró porque marzo y abril tienen prácticamente el mismo volumen mensual: 5000 pedidos en ambos, 27 587 frente a 27 891 paquetes y 608 frente a 611 bloqueos. Se conserva junio para aumentar la separación temporal y aportar un mes con más bloqueos.

## Archivos exclusivos

- `campana-alns100-tabu300-4x3.ps1`: ejecuta y reanuda la campaña.
- `analisis-alns100-tabu300-4x3.py`: valida los 24 resultados y realiza el análisis pareado por fecha y semilla.
- `resultados/campana-alns100-tabu300-4x3/`: CSV, movimientos, entregas, metadatos, manifiesto y análisis.

Los cambios compartidos del planificador, las métricas mediana/P90 de `Ta` y el control cooperativo del presupuesto quedan aislados por la rama. No deben incorporarse a la campaña de 300/300 sin revisar su protocolo.

## Ejecución

```powershell
.\algoritmos\compilar.bat -Pruebas
.\algoritmos\experimentacion\campana-alns100-tabu300-4x3.ps1 -DryRun
.\algoritmos\experimentacion\campana-alns100-tabu300-4x3.ps1 -NoCompilar
```

El script omite una corrida cuando encuentra su `resumen.csv` completo. Si una carpeta quedó interrumpida, la preserva con el sufijo `-incompleta-<fecha-hora>` y repite solo esa corrida.

## Análisis final

```powershell
& 'C:\Users\fenix\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' algoritmos\experimentacion\analisis-alns100-tabu300-4x3.py --entrada algoritmos\experimentacion\resultados\campana-alns100-tabu300-4x3
```

El análisis considera como unidad experimental el par `fecha + semilla`. Los ciclos internos no se utilizan como repeticiones independientes. Se informan `Ta` promedio, mediana y P90, tiempos reales, holguras, completitud, colapso y métricas complementarias.
