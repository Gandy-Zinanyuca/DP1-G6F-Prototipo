// Averías de vehículos e incidencias en curso (manuales y aleatorias).
// Depende de: core/*, sim/vehicles.js, core/log.js
"use strict";
  /* =================== INCIDENTS =================== */
  // aplica una avería a una unidad — usado tanto por el disparo aleatorio como por el registro manual
  function applyAveria(v, ft, manual){
    const order = orders.find(o=>o.id===v.order);
    const dur = ft.minMin + Math.random()*(ft.maxMin-ft.minMin);
    const until = simMin + dur;
    incidents.push({type:'falla', pos:{...v.pos}, until, since:simMin, vehicleId:v.id, falla:ft});
    v.state='broken'; v.brokenUntil = until;
    if(order){ order.status='pending'; order.vehicleId = null; }
    addLog(`<b>Avería ${ft.label}</b>${manual?' (registrada manualmente)':''} en ${v.id}` +
      (order ? ` — carga del pedido #${order.id} vuelve a asignación.` : '.'), 'critical');
  }

  function triggerAveriaManual(vehicleId, tipo){
    const v = vehicles.find(vv=>vv.id===vehicleId);
    if(!v || (v.state!=='toClient' && v.state!=='returning')) return false;
    const ft = FALLA_TYPES.find(f=>f.tipo===tipo) || FALLA_TYPES[0];
    applyAveria(v, ft, true);
    return true;
  }

  let nextIncidentIn = 60 + Math.random()*60;
  function maybeTriggerIncident(dtMin){
    activateScheduledBlockages();

    nextIncidentIn -= dtMin;
    if(nextIncidentIn > 0) return;
    nextIncidentIn = 70 + Math.random()*110;

    const rollBlockage = !bloqueosQueue && Math.random() < 0.5;
    if(rollBlockage){
      const chain = randomBlockageChain();
      if(chain.length<2) return;
      const until = simMin + 60 + Math.random()*120;
      addBlockageChain(chain, until);
      const a=chain[0], b=chain[chain.length-1];
      addLog(`<b>Bloqueo vial</b> activo entre el nodo (${a.x},${a.y}) y (${b.x},${b.y}).`, 'warning');
    } else {
      const moving = vehicles.filter(v=>v.state==='toClient');
      if(!moving.length) return;
      const v = moving[Math.floor(Math.random()*moving.length)];
      if(!orders.find(o=>o.id===v.order)) return;
      const ft = FALLA_TYPES[Math.floor(Math.random()*FALLA_TYPES.length)];
      applyAveria(v, ft, false);
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
      }
      return false;
    });
  }

