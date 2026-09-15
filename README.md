# PaqRap — DP1 · Equipo 6F

Solución informática para **PaqRap**, empresa de reparto del producto «P», del curso
Desarrollo de Proyectos 1 (1INF54, PUCP, 2026-2).

PaqRap organiza en tiempo real qué unidad entrega qué pedido, desde qué almacén y dentro
de qué plazo, y **replanifica** cuando una avería o un bloqueo de calle lo obliga.

Este repositorio tiene dos partes independientes:

| Carpeta        | Qué es                                                        | Lenguaje |
| -------------- | ----------------------------------------------------------- | -------- |
| `Prototipo/`   | Visualizador y simulador de las operaciones (mapa en vivo)  | HTML/CSS/JS |
| `algoritmos/`  | Componente planificador: metaheurísticas de ruteo           | Java 11+ |

## Equipo

| Integrante         | A cargo de                                      |
| ------------------ | ----------------------------------------------- |
| Gandy Zinanyuca    | Escenarios e indicadores, flota y configuración |
| Eliezer Villarreal | Registro de pedidos, planificador de rutas      |
| Yaser Fernandez    | Almacenes e inventario, visualizador            |
| Lucas Alvites      | Detección de incidencias, replanificación       |

---

## `Prototipo/` — visualizador

Retícula urbana de 70×50 km (nodos cada 1 km), los 3 almacenes en posición fija, unidades en
ruta, bloqueos y averías señalados en el mapa, y un panel de indicadores con pestañas
(KPIs / registro de eventos).

Tres escenarios, elegibles en el modal de inicio, todos con la flota, la capacidad de los
almacenes intermedios y los turnos como parámetros:

- **Operación día a día** — alta manual de pedidos (individual y masiva); el reloj no arranca
  hasta el primer pedido.
- **Simulación de 5 días** — pedidos, bloqueos, averías y plan de mantenimiento **por archivo**;
  pide fecha/hora de inicio; corre un ciclo fijo de 5 días.
- **Hasta el colapso** — igual entrada por archivo; corre sin límite hasta el primer
  incumplimiento de plazo.

Módulos (rail lateral): **Mapa · Pedidos · Flota · Averías · Mantenimiento · Bloqueos**.
Las unidades se identifican `TTNN` (`TA` autos, `TB` bicicletas, `TM` motos).

### Cómo ejecutarlo

Usa rutas relativas, así que ábrelo con un servidor local:

```bash
cd Prototipo
python -m http.server 8000
# http://localhost:8000/
```

Cada `.js` empieza con dos líneas de comentario (`// qué hace` / `// Depende de:`). Los scripts
se cargan como `<script>` clásicos en el orden exacto de `index.html` y comparten el entorno
global; si reordenas esas etiquetas, revisa las dependencias.

### Relación con la demo publicada

> ▶ **Demo:** https://claude.ai/code/artifact/8c1a4e23-c13f-4706-b3ba-516103fa5b7b

La demo es **un único HTML autocontenido** (lo exige la plataforma). El código de `Prototipo/`
es la versión dividida por responsabilidad; la sincronización con la demo es **manual** y hoy
la demo va por delante (códigos `TTNN`, pestañas del panel, carga de averías/mantenimiento por
archivo, plan de mantenimiento preventivo). Ver `../../prototipos/checklist-paqrap-revision.md`.

---

## `algoritmos/` — componente planificador

RNF01 exige **dos soluciones metaheurísticas en Java**, comparadas por experimentación
numérica sobre la misma función objetivo y las mismas estructuras.

- `algoritmos/DISENO-ALGORITMOS.md` — formulación (MDVRPTW dinámico), función objetivo,
  pseudocódigo y trazabilidad con la Lista de Exigencias.
- `algoritmos/alns/` — **ALNS** (primera solución). Java 11, `javac` puro sin dependencias;
  paquete `pe.pucp.paqrap`. Se compila con `compilar.sh` / `.bat`. Ver su `README.md`.
- `algoritmos/tabu/` — **Búsqueda Tabú** (segunda solución). Java 17, **Maven** + JUnit 5;
  paquete `pe.logistica`. Traducción del pseudocódigo de la §5.1 de la ISA. Ver su `README.md`
  y `REFERENCIAS.md`.

```bash
# ALNS
cd algoritmos/alns && ./compilar.sh      # .class en out/ (ignorado)
java -cp out pe.pucp.paqrap.DemoPlanificador <ventas.txt> <bloqueos.txt> <mant.txt> --dia 1 --hora 6

# Tabú
cd algoritmos/tabu && mvn -q package     # target/ y el .jar están ignorados
java -jar target/planificador-tabu-1.0.0.jar --config config/referencia-20260909.properties
```

> **Nota:** hoy son dos bases de código independientes (distinto paquete, distinto build). El
> `DISENO-ALGORITMOS.md` plantea que compartan estructuras y función objetivo para que la
> comparación de la experimentación numérica sea válida — esa unificación está **pendiente**.
> Los datos de curso voluminosos (`algoritmos/tabu/datos/ventas/` y `.../bloqueos/`) **no se
> versionan** (van aparte, igual que en ALNS); sí se conservan `inventario.json`,
> `mant.preventivo.09.10.txt` y los `resultado-*.txt` publicados.

---

## Estructura

```
DP1-G6F-Prototipo/
├─ .gitattributes           normaliza fin de línea a LF
├─ .gitignore               out/, target/, *.class, *.jar, sources.txt, locks…
├─ README.md
├─ Documento de Vision/     doc. de visión del producto (PDF)
├─ Prototipo/
│  ├─ index.html            cabecera, mapa, modales + carga de scripts
│  ├─ css/styles.css        tokens de color claro/oscuro, layout, componentes
│  └─ js/
│     ├─ core/   config · state · time · grid (BFS) · log
│     ├─ data/   file-io (parseo de archivos del curso)
│     ├─ sim/    orders · blockages · incidents · vehicles · shift-restock · sim-step
│     ├─ ui/     render · dom-update · selection-panel · map-input · config-panel ·
│     │          data-panel · run-controls · modules-nav · incidencias-module ·
│     │          search-filter · pedidos-module
│     └─ main.js  bucle de animación + arranque (último)
└─ algoritmos/
   ├─ DISENO-ALGORITMOS.md
   ├─ alns/                 ALNS · javac · paquete pe.pucp.paqrap
   │  ├─ README.md · DISENO-ALGORITMOS.md · compilar.sh / .bat
   │  └─ src/pe/pucp/paqrap/  DemoPlanificador · PruebaPlanificador ·
   │                          alns/ · datos/ · mapa/ · modelo/ · planificador/ · solucion/
   └─ tabu/                 Búsqueda Tabú · Maven + JUnit 5 · paquete pe.logistica
      ├─ README.md · REFERENCIAS.md · pom.xml
      ├─ config/            referencia-20260909.properties
      ├─ datos/             inventario.json · mant.preventivo.09.10.txt  (ventas/ y bloqueos/ ignorados)
      ├─ resultado-*.txt    salidas publicadas 09/09/2026
      └─ src/{main,test}/java/pe/logistica/
```
