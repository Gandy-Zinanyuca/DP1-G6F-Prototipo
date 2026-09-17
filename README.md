# PaqRap — DP1 · Equipo 6F

Solución informática para **PaqRap**, empresa de reparto del producto «P», del curso
Desarrollo de Proyectos 1 (1INF54, PUCP, 2026-2).

PaqRap organiza en tiempo real qué unidad entrega qué pedido, desde qué almacén y dentro
de qué plazo, y **replanifica** cuando una avería o un bloqueo de calle lo obliga.

Este repositorio tiene dos partes independientes:

| Carpeta        | Qué es                                                        | Lenguaje |
| -------------- | ----------------------------------------------------------- | -------- |
| `Prototipo/`   | Visualizador y simulador de las operaciones (mapa en vivo)  | HTML/CSS/JS |
| `algoritmos/`  | Componente planificador: metaheurísticas de ruteo           | Java 17+ |

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
es esa misma versión dividida por responsabilidad, re-sincronizada el 2026-09-16. La
sincronización sigue siendo **manual**: cuando haya cambios de fondo en la demo, hay que
volver a repartirlos entre los archivos de `js/` (o volver a generarlos con el mismo criterio:
un archivo por sección `/* === … === */` del `<script>` de la demo). Ver
`../../prototipos/checklist-paqrap-revision.md`.

---

## `algoritmos/` — componente planificador

Se conserva ALNS de `algorithms` y se integra Tabu Search de `dev/yaser`, con sus dependencias. Ambos se construyen con JDK 17 y sin Maven.

Consultar la [guía vigente de ejecución](algoritmos/README.md) y los [metadatos de pruebas](algoritmos/METADATOS-PRUEBAS.md).

```bat
algoritmos\compilar.bat -Pruebas
```

Los lanzadores `algoritmos/ejecutar-alns.bat` y `algoritmos/ejecutar-tabu.bat` permiten probar cada motor por separado. Sus modelos y evaluadores todavía son diferentes: la integración no equivale a una comparación experimental bajo reglas idénticas.

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
│     ├─ data/   file-io (parsea ventas, bloqueos, averías y plan de mantenimiento)
│     ├─ sim/    orders · blockages · incidents (averías + mantenimiento) · vehicles ·
│     │          shift-restock · sim-step
│     ├─ ui/     render · dom-update · selection-panel · map-input · config-panel ·
│     │          file-upload (genérico, con modal de confirmación) · run-controls ·
│     │          nav-rail · averias-module · bloqueos-module · flota-module ·
│     │          mantenimiento-module · search-filter · indicators-panel ·
│     │          pedidos-module
│     └─ main.js  bucle de animación + arranque (último)
└─ algoritmos/
   ├─ README.md · METADATOS-PRUEBAS.md
   ├─ compilar.bat · compilar.ps1
   ├─ ejecutar-alns.bat · ejecutar-tabu.bat
   ├─ alns/                 ALNS conservado de algorithms
   │  ├─ src/ · data/ · README.md
   │  └─ simular.bat        arnés mensual simplificado
   ├─ tabu/                 TS de dev/yaser · sin Maven
   │  └─ src/main/java/pe/pucp/paqrap/tabu/
   ├─ comun/                núcleo estricto utilizado por TS
   └─ experimentacion/      lanzador TS y pruebas sin JUnit
```
