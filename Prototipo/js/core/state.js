// Estado mutable global de la simulación (reloj, pedidos, flota, incidentes, colas de archivo, stats, flags de escenario) + buildFleet().
// Depende de: core/config.js
"use strict";
  /* =================== STATE =================== */
  let CYCLE_DAYS_5D = 5;
  let scenario = '5d';            // '5d' | 'diaria' — elegido por el usuario antes de iniciar
  let simMin = 7*60;               // start at 07:00 on day 1
  let runStartSimMin = 7*60;       // simMin al momento de arrancar la corrida actual — para el contador de tiempo simulado transcurrido
  let cycleDay = 1;
  let lastTs = null;
  let running = false;             // el usuario controla inicio/detención explícitamente
  let startedAtReal = null;
  let accumulatedRunMs = 0;
  let simEpochDate = new Date(2026,8,1); // fecha calendario que corresponde a simMin=0 — elegible antes de iniciar

  let ventasQueue = null;   // null = generación aleatoria; array = registro masivo cargado desde archivo
  let ventasPtr = 0;
  let bloqueosQueue = null; // null = generación aleatoria; array = bloqueos programados desde archivo
  let averiasQueue = null;  // null = disparo aleatorio; array = averías programadas desde archivo (5D / colapso)
  let mantenimientoQueue = null; // null = sin archivo; array = mantenimientos programados desde archivo (5D / colapso)

  let orderSeq = 1000;
  let orders = [];        // pending / assigned / atClient
  let flashes = [];       // brief delivered/failed bursts {pos,color,born}
  let incidents = [];     // {type:'bloqueo', nodes, edges, until} | {type:'falla', pos, until, vehicleId, falla}
  let vehicles = [];
  let blockedEdges = new Set();  // canonical "x1,y1-x2,y2" keys for currently-blocked street segments
  let stats = { deliveredTotal:0, deliveredToday:0, onTime:0, late:0, cost:0, byPriority:{}, bySector:{} };
  let logs = [];

  // semáforo (cumplimiento de pedidos + nivel de inventario): % que separa cada color — configurable
  let thresholds = { green:70, amber:35 };

  // cámara del mapa (zoom / desplazamiento) — solo navegación, no controla la simulación
  let camera = { scale:1, cx:0.5, cy:0.5 };
  let selected = null;   // {type:'vehicle'|'warehouse'|'zone', ...} for the detail panel
  let typeFilter = { auto:true, moto:true, bici:true }; // visibilidad por tipo en el mapa
  let showRoutes = true; // mostrar/ocultar la línea de ruta asignada de cada vehículo (LE checklist #33)
  let gateConfirmed = false; // se vuelve true tras la primera confirmación del modal de inicio
  let diariaWaiting = false; // 'operación diaria': true hasta que se registre el primer pedido
  let colapsoTriggered = false; // 'colapso': true en cuanto la primera entrega incumple su plazo
  let orderHistory = []; // copia de pedidos ya cerrados (entregado / no cumplido), para el módulo de pedidos
  let fleetConfig = { auto:6, moto:10, bici:8 }; // composición de flota — configurable antes de iniciar (LE067/LE068)
  let shiftStarts = [7*60, 15*60, 23*60]; // horarios de cambio de turno (minutos desde medianoche) — configurable antes de iniciar, por defecto 07:00/15:00/23:00

  function resetPriorityStats(){
    PRIORITY_BUCKETS.forEach(p=>{ stats.byPriority[p] = { delivered:0, onTime:0 }; });
  }
  resetPriorityStats();

  function css(varName){ return getComputedStyle(document.documentElement).getPropertyValue(varName).trim(); }

  function riskFraction(o){
    const total = o.deadline - o.createdAt;
    const remain = o.deadline - simMin;
    return total>0 ? Math.max(0, remain/total)*100 : 0;
  }
  function riskLevel(pct){
    if(pct >= thresholds.green) return 'good';
    if(pct >= thresholds.amber) return 'warning';
    return 'critical';
  }
  function riskColorOf(o){ return css('--'+riskLevel(riskFraction(o))); }

  /* ---- build fleet: todas las unidades salen, la primera vez, del almacén central ---- */
  function buildFleet(){
    vehicles = [];
    const counts = { auto:fleetConfig.auto, moto:fleetConfig.moto, bici:fleetConfig.bici };
    Object.keys(counts).forEach(typeKey=>{
      const t = VEHICLE_TYPES[typeKey];
      for(let i=0;i<counts[typeKey];i++){
        vehicles.push({
          id: ({auto:'TA', moto:'TM', bici:'TB'})[typeKey] + String(i+1).padStart(2,'0'),
          type: typeKey,
          capacity: t.capacity,
          speed: t.speed,
          costPerKm: t.cost,
          home: 'central',    // almacén donde está basada actualmente (cambia al regresar a recargar)
          state:'idle',        // idle | toClient | atClient | returning | broken | break
          pos:{x:WAREHOUSES.central.pos.x, y:WAREHOUSES.central.pos.y},
          path:null, pathIdx:0,
          order:null,
          timer:0,
          heading:0,
          trail:[],
        });
      }
    });
  }
