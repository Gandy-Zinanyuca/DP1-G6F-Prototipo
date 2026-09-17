// Averías y mantenimiento de vehículos: disparo manual/aleatorio/por archivo, y su resolución al vencer.
// Depende de: core/*, sim/vehicles.js, core/log.js, core/time.js
"use strict";
  /* =================== INCIDENTS =================== */
  // aplica una avería a una unidad — usado por el disparo aleatorio, el registro manual y el archivo programado
  // origin: 'manual' | 'archivo' | null (aleatorio)
  function applyAveria(v, ft, origin){
    const order = orders.find(o=>o.id===v.order);
    const dur = ft.minMin + Math.random()*(ft.maxMin-ft.minMin);
    const until = simMin + dur;
    incidents.push({type:'falla', pos:{...v.pos}, until, since:simMin, vehicleId:v.id, falla:ft});
    v.state='broken'; v.brokenUntil = until;
    if(order){ order.status='pending'; order.vehicleId = null; order.reprogramado = true; }
    const tag = origin==='manual' ? ' (registrada manualmente)' : origin==='archivo' ? ' (programada por archivo)' : '';
    addLog(`<b>Avería ${ft.label}</b>${tag} en ${v.id}` +
      (order ? ` — carga del pedido #${order.id} vuelve a asignación.` : '.'), 'critical');
  }

  function triggerAveriaManual(vehicleId, tipo){
    const v = vehicles.find(vv=>vv.id===vehicleId);
    if(!v || (v.state!=='toClient' && v.state!=='returning')) return false;
    const ft = FALLA_TYPES.find(f=>f.tipo===tipo) || FALLA_TYPES[0];
    applyAveria(v, ft, 'manual');
    return true;
  }

  // aplica las averías cargadas desde archivo cuya hora programada ya llegó y cuya unidad está en ruta
  function activateScheduledAverias(){
    if(!averiasQueue) return;
    averiasQueue.forEach(rec=>{
      if(rec.done || rec.atMin > simMin) return;
      const v = vehicles.find(vv=>vv.id===rec.vehicleId);
      if(v && (v.state==='toClient' || v.state==='returning')){
        const ft = FALLA_TYPES.find(f=>f.tipo===rec.tipo) || FALLA_TYPES[0];
        applyAveria(v, ft, 'archivo');
        rec.done = true;
      } else if(rec.atMin <= simMin - 120){
        rec.done = true; // ventana vencida: la unidad nunca estuvo en ruta cerca de esa hora
        addLog(`Avería programada de ${rec.vehicleId} descartada: la unidad no estaba en ruta a esa hora.`, 'warning');
      }
    });
  }

  // mantenimiento preventivo. origin: 'manual' | 'archivo'.
  // untilOverride: minuto de simulación en que termina (para el plan por archivo, 00:00 del día siguiente)
  function applyMantenimiento(v, horas, origin, untilOverride){
    const until = untilOverride != null ? untilOverride : simMin + horas*60;
    incidents.push({type:'mantenimiento', pos:{...v.pos}, until, since:simMin, vehicleId:v.id, horas});
    v.state='maintenance';
    const tag = origin==='archivo' ? ' (plan de mantenimiento preventivo)' : '';
    addLog(`<b>Mantenimiento</b>${tag} programado para ${v.id} (${horas} h).`, 'accent');
  }
  function triggerMantenimientoManual(vehicleId, horas){
    const v = vehicles.find(vv=>vv.id===vehicleId);
    if(!v || (v.state!=='idle' && v.state!=='break')) return false;
    applyMantenimiento(v, Math.max(1, horas||1), 'manual');
    return true;
  }

  // plan de mantenimiento preventivo cargado desde archivo (aaaammdd:TTNN):
  // el día programado, la unidad sale de ruta a las 00:00 (Nota 1/2) y no vuelve hasta las 23:59
  function activateScheduledMantenimiento(){
    if(!mantenimientoQueue) return;
    const epoch = new Date(simEpochDate.getFullYear(), simEpochDate.getMonth(), simEpochDate.getDate());
    mantenimientoQueue.forEach(rec=>{
      if(rec.done) return;
      const dayIndex = Math.round((new Date(rec.y, rec.mo-1, rec.d) - epoch) / 86400000);
      if(dayIndex < 0){ rec.done = true; return; } // fecha anterior al inicio de la corrida
      const startMin = dayIndex * 1440;
      const endMin = startMin + 1440; // 00:00 del día siguiente
      if(simMin < startMin) return;   // aún no llega su día
      if(simMin >= endMin){
        if(!rec.warned){ addLog(`Mantenimiento de ${rec.vehicleId} (${rec.dateStr}) no se aplicó: unidad ausente ese día.`, 'warning'); rec.warned = true; }
        rec.done = true; return;
      }
      const v = vehicles.find(vv=>vv.id===rec.vehicleId);
      if(!v) return; // la unidad puede no existir en la flota configurada; se reintenta hasta que pase el día
      // Nota 1 + Nota 2: la unidad sale de ruta de inmediato, aunque lleve un pedido
      const order = v.order ? orders.find(o=>o.id===v.order) : null;
      if(order){ order.status='pending'; order.vehicleId = null; order.reprogramado = true; }
      v.order = null; v.path = null; v.pathIdx = 0; v.trail = [];
      applyMantenimiento(v, Math.max(1, Math.round((endMin - simMin)/60)), 'archivo', endMin);
      rec.done = true;
    });
  }

  let nextIncidentIn = 60 + Math.random()*60;
  function maybeTriggerIncident(dtMin){
    activateScheduledBlockages();
    activateScheduledAverias();
    activateScheduledMantenimiento();

    nextIncidentIn -= dtMin;
    if(nextIncidentIn > 0) return;
    nextIncidentIn = 70 + Math.random()*110;

    // si un tipo de incidencia viene de archivo, ya no se genera al azar
    const canBlock = !bloqueosQueue;
    const canFail = !averiasQueue;
    if(!canBlock && !canFail) return;

    const rollBlockage = canBlock && (!canFail || Math.random() < 0.5);
    if(rollBlockage){
      const chain = randomBlockageChain();
      if(chain.length<2) return;
      const until = simMin + 60 + Math.random()*120;
      addBlockageChain(chain, until);
      const a=chain[0], b=chain[chain.length-1];
      addLog(`<b>Bloqueo vial</b> activo entre el nodo (${a.x},${a.y}) y (${b.x},${b.y}).`, 'warning');
    } else if(canFail){
      const moving = vehicles.filter(v=>v.state==='toClient');
      if(!moving.length) return;
      const v = moving[Math.floor(Math.random()*moving.length)];
      if(!orders.find(o=>o.id===v.order)) return;
      const ft = FALLA_TYPES[Math.floor(Math.random()*FALLA_TYPES.length)];
      applyAveria(v, ft, null);
    }
  }

  function updateIncidents(){
    incidents = incidents.filter(inc=>{
      if(simMin < inc.until) return true;
      if(inc.type==='falla'){
        const v = vehicles.find(vv=>vv.id===inc.vehicleId);
        if(v && v.state==='broken'){
          const wh = pickReturnWarehouse(v.pos);
          v.state='returning';
          v.path = computeRoute(v.pos, wh.pos);
          v.pathIdx = 0;
          v.returnTarget = wh.id;
          addLog(`${v.id} reparada — regresando a ${wh.name}.`, 'good');
        }
      } else if(inc.type==='bloqueo'){
        removeBlockageChain(inc);
        addLog(`Bloqueo entre (${inc.nodes[0].x},${inc.nodes[0].y}) y (${inc.nodes[inc.nodes.length-1].x},${inc.nodes[inc.nodes.length-1].y}) despejado.`, 'good');
      } else if(inc.type==='mantenimiento'){
        const v = vehicles.find(vv=>vv.id===inc.vehicleId);
        if(v && v.state==='maintenance'){
          v.state='idle';
          addLog(`Mantenimiento de ${v.id} finalizado — unidad disponible.`, 'good');
        }
      }
      return false;
    });
  }
