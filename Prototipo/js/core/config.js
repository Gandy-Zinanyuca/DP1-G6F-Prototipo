// Constantes de configuración: velocidad de la simulación, tamaño de la retícula, tipos de vehículo, almacenes, prioridades, tipos de avería.
// Depende de: nada (es la base)
"use strict";
  /* =================== CONFIG =================== */
  const SIM_MIN_PER_SEC = 10;      // simulated minutes per real second
  const GRID_W = 70, GRID_H = 50;  // city grid: 70 km (X) by 50 km (Y), nodes every 1 km
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const VEHICLE_TYPES = {
    auto:  { key:'auto',  label:'Auto',      capacity:24, speed:40, cost:8, color:'--veh-car',  emoji:'🚗' },
    moto:  { key:'moto',  label:'Moto',      capacity:8,  speed:25, cost:6, color:'--veh-moto', emoji:'🏍️' },
    bici:  { key:'bici',  label:'Bicicleta', capacity:4,  speed:12, cost:3, color:'--veh-bike', emoji:'🚲' },
  };

  // posiciones fijas confirmadas: almacén central (27,14), Nor-Oeste (12,38), Este (57,27)
  const WAREHOUSES = {
    central:  { id:'central',  name:'Almacén Central',    pos:{x:27,y:14}, infinite:true, dispatchedToday:0 },
    noroeste: { id:'noroeste', name:'Almacén Nor-Oeste',  pos:{x:12,y:38}, capacity:1000, stock:760, dispatchedToday:0 },
    este:     { id:'este',     name:'Almacén Este',       pos:{x:57,y:27}, capacity:1000, stock:840, dispatchedToday:0 },
  };

  const PRIORITY_BUCKETS = [4, 8, 12, 18, 36];

  const FALLA_TYPES = [
    { tipo:1, label:'tipo 1 (leve)',     minMin:20, maxMin:40,  color:'--warning', icon:'🟡' },
    { tipo:2, label:'tipo 2 (moderada)', minMin:45, maxMin:80,  color:'--accent-2', icon:'🟠' },
    { tipo:3, label:'tipo 3 (grave)',    minMin:90, maxMin:150, color:'--critical', icon:'🔴' },
  ];

  const SHIFT_DUR = 8*60; // cada turno dura 8 horas (fijo); solo la hora de inicio es configurable
