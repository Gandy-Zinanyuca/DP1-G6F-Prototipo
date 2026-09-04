# PaqRap en Ruta

Prototipo del sistema de planificación y monitoreo de entregas **PaqRap**,
para el curso Desarrollo de Proyectos 1 (DP1) — equipo **H983-Eq6F**.

PaqRap organiza en tiempo real qué vehículo entrega qué pedido, desde qué
almacén, y dentro de qué plazo — incluso cuando una avería o un bloqueo de
calle obliga a replanificar sobre la marcha.

▶ **Demo en vivo:** https://claude.ai/code/artifact/8c1a4e23-c13f-4706-b3ba-516103fa5b7b

## Equipo

| Integrante         | Módulos a cargo                                 |
| ------------------ | ----------------------------------------------- |
| Gandy Zinanyuca    | Escenarios e indicadores, flota y configuración |
| Eliezer Villarreal | Registro de pedidos, planificador de rutas      |
| Yaser Fernandez    | Almacenes e inventario, visualizador            |
| Lucas Alvites      | Detección de incidencias, replanificación       |

## Qué simula el prototipo

Tres escenarios, seleccionables al iniciar cualquier ejecución:

- **Operación diaria** — registro manual de pedidos, uno por uno; el reloj
  de simulación no arranca hasta que se registra el primer pedido.
- **Simulación de 5 días** — permite carga masiva de pedidos y bloqueos
  desde archivo, pide fecha y hora de inicio, corre un ciclo fijo de 5 días.
- **Simulación hasta el colapso** — corre sin límite de tiempo hasta que un
  primer pedido incumple su plazo comprometido; ahí la ejecución se detiene
  y reporta el colapso.

Antes de iniciar cualquiera de los tres, el modal de inicio permite
configurar como **parámetros**: la composición de la flota (autos, motos,
bicicletas), la capacidad de los almacenes intermedios, y los horarios de
cambio de turno (por defecto 07:00 / 15:00 / 23:00, turnos de 8 h).

## Módulos del prototipo

- **Mapa / simulación** — retícula urbana de 70×50 km (nodos cada 1 km),
  con los 3 almacenes en posiciones fijas, vehículos en ruta, bloqueos y
  averías señalados gráficamente, semáforo de cumplimiento configurable,
  búsqueda de unidad por código y filtro por tipo de vehículo.
- **Registro de pedidos** — alta manual y carga masiva, ambas con modal de
  confirmación antes de guardar, búsqueda por cliente, y filtros por estado
  y modalidad de entrega (regular 36 h / priorizada 4-18 h).
- **Incidencias** — registro manual de averías (tipo 1/2/3) y bloqueos de
  tramos de calle, con tabla en vivo de incidencias activas.
- **Panel de indicadores** — pedidos activos, cumplimiento de SLA, entregas
  del día, incumplimientos, cumplimiento por prioridad, y estado de cada
  almacén.

Controles de cabecera: **Iniciar/Detener** la ejecución y **Reiniciar**
(vuelve al modal de inicio para configurar una corrida nueva).

## Cómo ejecutarlo

El proyecto usa rutas relativas (`css/styles.css`, `js/...`), así que ábrelo
con un servidor local en vez de doble clic:

```bash
cd paqrap-en-ruta
python -m http.server 8000
```

y entra a `http://localhost:8000/`.

## Estructura del proyecto

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
`// Depende de:`) a modo de documentación. Los scripts se cargan como
`<script>` clásicos, en el orden exacto en que aparecen en `index.html`, y
comparten el mismo entorno global. Si reordenas esas etiquetas, revisa que
ningún archivo use algo que el siguiente todavía no haya declarado.

**Siguiente paso natural** (no hecho todavía para no arriesgar romper nada):
convertir estos scripts a módulos ES (`type="module"`, `import`/`export`
explícitos) para que la dependencia entre archivos la garantice el
navegador y no solo el comentario.

## Relación con la demo publicada

El link de demo de arriba es **un solo archivo HTML autocontenido** (así lo
exige la plataforma donde está publicado). Este repositorio es el código
fuente real, dividido por responsabilidad; cuando hay cambios de fondo en la
simulación, se replican a mano en esa versión de archivo único antes de
volver a publicarla.
