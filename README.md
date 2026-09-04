# PaqRap en Ruta — código fuente

Versión del prototipo **PaqRap en Ruta** dividida en archivos por responsabilidad,
en vez del HTML único de ~2700 líneas con el que se venía trabajando. El
comportamiento es exactamente el mismo — es un corte mecánico del mismo código,
no una reescritura.

## Cómo abrirlo

Como usa rutas relativas (`css/styles.css`, `js/...`), ábrelo con un servidor
local en vez de doble clic (algunos navegadores restringen scripts locales
sobre `file://`):

```bash
cd D:\2026-2\DP1\prototipos\paqrap-en-ruta
python -m http.server 8000
```

y entra a `http://localhost:8000/`.

## Estructura

```
paqrap-en-ruta/
├─ index.html            estructura de la página (cabecera, mapa, modales) + carga los scripts
├─ css/
│  └─ styles.css         todos los estilos (tokens de color claro/oscuro, layout, componentes)
└─ js/
   ├─ core/              piezas base que usa casi todo lo demás
   │  ├─ config.js          constantes: velocidad de simulación, retícula, tipos de vehículo, almacenes...
   │  ├─ state.js            estado mutable global (reloj, pedidos, flota, flags de escenario) + buildFleet()
   │  ├─ time.js              turnos, refrigerio, formato de hora
   │  ├─ grid.js               geometría de la retícula y pathfinding (BFS)
   │  └─ log.js                bitácora de eventos (addLog)
   ├─ data/
   │  └─ file-io.js         parseo de los archivos de ventas y bloqueos (formato del curso)
   ├─ sim/                el "motor" de la simulación
   │  ├─ orders.js           ciclo de vida de un pedido
   │  ├─ blockages.js         bloqueos de tramos de calle
   │  ├─ incidents.js          averías e incidencias
   │  ├─ vehicles.js            movimiento de vehículos
   │  ├─ shift-restock.js        refrigerios y recarga diaria de almacenes
   │  └─ sim-step.js              un paso de simulación + resetSimulation()
   └─ ui/                 todo lo que dibuja o reacciona a la interfaz
      ├─ render.js           dibujo del mapa en <canvas>
      ├─ dom-update.js        refresco periódico de textos/KPIs
      ├─ selection-panel.js    panel de detalle (clic en vehículo/almacén)
      ├─ map-input.js           zoom / paneo / clic sobre el mapa
      ├─ config-panel.js         umbrales del semáforo
      ├─ data-panel.js            carga de archivos de ventas/bloqueos
      ├─ run-controls.js           start/stop, modal de inicio, Reiniciar
      ├─ modules-nav.js             desplegable mapa/pedidos/incidencias
      ├─ incidencias-module.js       registro manual de averías y bloqueos
      ├─ search-filter.js             buscar unidad / filtrar por tipo
      └─ pedidos-module.js             alta manual, carga masiva y sus modales de confirmación
   └─ main.js             bucle de animación + arranque de la app (se carga último)
```

Cada archivo `.js` empieza con un comentario de dos líneas (`// qué hace` /
`// Depende de:`) a modo de documentación — no hay un `import`/`export` real
todavía: los scripts se cargan como `<script>` clásicos, en el orden exacto
que aparece en `index.html`, y comparten el mismo entorno global — igual que
antes cuando todo vivía en un único `(function(){ ... })()`. Si reordenas las
etiquetas `<script>`, revisa que ningún archivo use algo que el siguiente
todavía no haya declarado.

**Siguiente paso natural** (no hecho aquí para no arriesgar romper nada): convertir
estos scripts a módulos ES (`type="module"`, `import`/`export` explícitos) para
que la dependencia entre archivos quede garantizada por el navegador y no solo
documentada en el comentario.

## Relación con el Artifact publicado

El prototipo que está publicado como Artifact (el link que usamos para
mostrarlo/probarlo) sigue siendo **un solo archivo HTML autocontenido**,
porque esa plataforma solo admite un archivo. Esta carpeta es el código
fuente "de verdad" para el repositorio del curso; cuando haya cambios de
fondo en la simulación, se replican a mano en el HTML único antes de volver a
publicarlo.
