# PaqRap — Componente planificador (ALNS)

Implementación en Java del primero de los dos algoritmos metaheurísticos del componente
planificador del sistema PaqRap (curso 1INF54, Equipo 6F, 2026-2).

La implementación sigue el pseudocódigo del Informe de Selección de Algoritmos (ISA v3.0, sección 5.2):
restricciones duras, costo = distancia × costo por km, ventana de consumo Sc = Sa × K. El detalle está en
**[`DISENO-ALGORITMOS.md`](DISENO-ALGORITMOS.md)**.

---

## Requisitos

Java 11 o superior. Sin dependencias externas ni herramienta de construcción.

## Compilar

Desde la raíz del proyecto:

```bash
# Linux / macOS
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
```

```bat
REM Windows
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
```

## Datos

Los archivos de entrada están en `data/`, con rutas relativas a esta carpeta:

```
data/
├── ventas.v20260909/     ventas.AAAAMM.txt   (2026-01 a 2028-12)
├── bloqueos.v20260909/   bloqueo.AAMM.txt    (2026-01 a 2028-12)
└── mant.preventivo.09.10.txt
```

## Ejecutar

Todos los comandos se ejecutan desde `algoritmos/alns`, después de `compilar.bat`.

**Simular un mes completo** (hasta el colapso o hasta fin de mes). La salida queda en
`resultados/sim_AAAAMM.txt` y al final se muestra el resumen o la línea de colapso:

```bat
simular.bat 202609
```

Por defecto usa `--dia 1 --hora 0 --ciclos 4464 --sa 10 --k 7 --iteraciones 300`. Cualquier opción
adicional reemplaza a la de por defecto, por ejemplo `simular.bat 202609 --iteraciones 1000 --k 12`.

**Comando equivalente sin el script:**

```bat
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador data\ventas.v20260909\ventas.202609.txt data\bloqueos.v20260909\bloqueo.2609.txt data\mant.preventivo.09.10.txt --dia 1 --hora 0 --ciclos 4464 --sa 10 --k 7 --iteraciones 300 > sim_202609.txt
```

Opciones de `DemoPlanificador`: `--dia N`, `--hora N`, `--ciclos N`, `--sa MIN` (Sa), `--k N` (K),
`--iteraciones N`, `--semilla N`, `--anio AAAA`, `--mes MM` (por defecto se deducen del nombre del
archivo de ventas) y `--traza`. Si no existe plan factible, reporta `COLAPSO LOGÍSTICO` y se detiene.

**Verificación.** Comprueba factibilidad, que ALNS no empeora la solución inicial, las métricas de
candidatos, la cartera de operadores (5 + 2), reproducibilidad, integridad, capacidad, plazos e
inventario en un instante dado:

```bat
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.PruebaPlanificador data\ventas.v20260909\ventas.202601.txt data\bloqueos.v20260909\bloqueo.2601.txt --dia 3 --hora 10 --iteraciones 500
```

En Windows, `-Dfile.encoding=UTF-8` evita que los acentos salgan como `?` en la consola.

---

## Estructura del código

```
src/pe/pucp/paqrap/
├── modelo/          Dominio: Coordenada, TipoVehiculo, Vehiculo, Almacen, Pedido,
│                    Bloqueo, Averia, Mantenimiento, Turnos
├── mapa/            MapaUrbano: retícula 71×51 y CAMINO_MÁS_RÁPIDO (Dijkstra temporal)
├── datos/           Lectores de ventas, bloqueos y mantenimiento; Instancia
├── solucion/        Ruta (restricciones duras por ruta) y Solucion (EVALUAR y costo)
├── planificador/    Planificador (interfaz), PlanificadorALNS, ContextoPlanificacion,
│                    ParametrosPlanificador
├── alns/            ALNS, ConstructorInicial, EvaluadorInsercion,
│                    SelectorAdaptativo, CriterioAceptacion, ParametrosALNS
│   ├── destruccion/ 5 operadores (2 propios del dominio)
│   └── reparacion/  2 operadores (voraz y arrepentimiento)
├── DemoPlanificador.java
└── PruebaPlanificador.java
```

La interfaz `Planificador` es el punto de extensión que permite intercambiar ALNS y Búsqueda Tabú
sobre el mismo contexto de planificación.

---

## Formato de los archivos de entrada

| Archivo | Formato de línea | Ejemplo |
|---|---|---|
| Ventas | `DDdHHhMMm:x,y,cCCCC,cantidad,plazo` | `01d01h30m:56,30,c4910,02,36` |
| Bloqueos | `DDdHHhMMm-DDdHHhMMm:x1,y1,x2,y2,...` | `01d02h22m-01d04h42m:25,45,45,45,45,40` |
| Mantenimiento | `AAAAMMDD:TXNN` | `20260901:TA01` |

El plazo se expresa en horas: 36 para la modalidad regular, 4/8/12/18 para la priorizada. En el
archivo de bloqueos, cada par consecutivo de vértices define un tramo cerrado en ambos sentidos.
Los códigos de unidad usan el prefijo `TA` para autos, `TM` para motos y `TB` para bicicletas.

## Configuración por defecto

Flota de 10 autos, 15 motos y 12 bicicletas (los códigos que aparecen en el archivo de
mantenimiento preventivo). Almacén central en (25, 15) con inventario ilimitado; intermedios en
(12, 38) y (55, 27) con 1 000 unidades. Todo es configurable por parámetro sin recompilar:
`Instancia.construir`, `ParametrosPlanificador` y `ParametrosALNS` (ConfiguracionALNS del ISA).
