# PaqRap — Componente planificador (ALNS)

Implementación en Java del primero de los dos algoritmos metaheurísticos del componente
planificador del sistema PaqRap (curso 1INF54, Equipo 6F, 2026-2).

El diseño completo —pseudocódigo, estructuras de datos, función objetivo, operadores,
parámetros y trazabilidad con la Lista de Exigencias— está en **[`DISENO-ALGORITMOS.md`](DISENO-ALGORITMOS.md)**.

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

## Ejecutar

**Planificación sobre datos reales.** Ejecuta ciclos consecutivos de 15 minutos simulados e
imprime la asignación de rutas del primer ciclo, las estadísticas del ALNS y el resumen final.

```bash
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.DemoPlanificador \
     ../ventas.v20260909/ventas.v20260909/ventas.202811.txt \
     ../bloqueos.v20260909/bloqueos/bloqueo.2811.txt \
     ../mant.preventivo.09.10.txt \
     --dia 1 --hora 6 --ciclos 24
```

Opciones: `--dia N`, `--hora N`, `--ciclos N`, `--horizonte H` (horas del horizonte de atención),
`--semilla N`, `--traza`.

**Verificación.** Comprueba que el ALNS mejora la heurística constructiva, que es reproducible y
que la asignación respeta integridad de pedidos, capacidad e inventario.

```bash
java -Dfile.encoding=UTF-8 -cp out pe.pucp.paqrap.PruebaPlanificador \
     ../ventas.v20260909/ventas.v20260909/ventas.202811.txt \
     ../bloqueos.v20260909/bloqueos/bloqueo.2811.txt --dia 15 --hora 12
```

En Windows, `-Dfile.encoding=UTF-8` evita que los acentos salgan como `?` en la consola.

---

## Estructura del código

```
src/pe/pucp/paqrap/
├── modelo/          Dominio: Coordenada, TipoVehiculo, Vehiculo, Almacen, Pedido,
│                    Bloqueo, Averia, Mantenimiento, Turnos
├── mapa/            MapaUrbano: retícula 71×51, arcos cerrados por bloqueo, BFS memorizada
├── datos/           Lectores de ventas, bloqueos y mantenimiento; Instancia
├── solucion/        Ruta (evaluación en dos pasadas) y Solucion (función objetivo)
├── planificador/    Planificador (interfaz), PlanificadorALNS, ContextoPlanificacion,
│                    ParametrosPlanificador
├── alns/            ALNS, ConstructorInicial, EvaluadorInsercion, CacheInserciones,
│                    SelectorAdaptativo, CriterioAceptacion, ParametrosALNS
│   ├── destruccion/ 6 operadores (2 propios del dominio)
│   └── reparacion/  5 operadores
├── DemoPlanificador.java
└── PruebaPlanificador.java
```

La interfaz `Planificador` es el punto de extensión: la Búsqueda Tabú se implementará contra ella
reutilizando `Ruta`, `Solucion` y `EvaluadorInsercion`, de modo que ambos algoritmos resuelvan
exactamente el mismo problema bajo la misma función objetivo. Esa es la condición para que la
comparación de la experimentación numérica sea válida.

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
`Instancia.construir`, `ParametrosPlanificador` y `ParametrosALNS`.
