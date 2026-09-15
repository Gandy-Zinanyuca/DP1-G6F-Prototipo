// Ciclo de vida de pedidos: creación aleatoria/desde archivo, asignación a vehículos, entrega/incumplimiento.
// Depende de: core/*, sim/vehicles.js, core/log.js
"use strict";
  /* =================== ORDERS / ASSIGNMENT =================== */
  let nextOrderIn = 3;
  const PRIORITY_OPTIONS = [36,36,36,18,12,8,4]; // weighted toward standard 36h

  function pushOrderFromRecord(rec){
    orders.push({
      id: orderSeq++, clientId: rec.clientId, pos: rec.pos, qty: rec.qty, priority: rec.hourLimit,
      createdAt: simMin, deadline: simMin + rec.hourLimit*60, status:'pending',
    });
  }

  function maybeSpawnOrder(dtMin){
    // operación diaria: los pedidos llegan únicamente por registro manual/masivo desde el módulo de Pedidos
    if(scenario==='diaria') return;
    // registro masivo cargado desde archivo: los pedidos llegan exactamente en su ##d##h##m programado
    if(ventasQueue){
      while(ventasPtr < ventasQueue.length && ventasQueue[ventasPtr].arriveMin <= simMin){
        pushOrderFromRecord(ventasQueue[ventasPtr++]);
      }
      return;
    }
    nextOrderIn -= dtMin;
    if(nextOrderIn > 0) return;
    const t = todMin();
    const active = t>=7*60 && t<23*60;
    nextOrderIn = active ? (3 + Math.random()*6) : (14 + Math.random()*22);

    const qty = 1 + Math.floor(Math.random()*22);
    const priority = PRIORITY_OPTIONS[Math.floor(Math.random()*PRIORITY_OPTIONS.length)];
    const pos = randomOrderPos();
    const o = {
      id: orderSeq++,
      clientId: randomClientId(),
      pos, qty, priority,
      createdAt: simMin,
      deadline: simMin + priority*60,
      status:'pending',
    };
    orders.push(o);
  }

  // devuelve TODOS los almacenes con stock suficiente, del más al menos cercano — tryAssign los
  // recorre en ese orden para no descartar un pedido solo porque el más cercano no tenga unidad libre.
  function dispatchWarehouseCandidates(qty, toPos){
    const candidates = Object.values(WAREHOUSES).filter(w => w.infinite || w.stock >= qty);
    candidates.sort((a,b)=> dist(a.pos,toPos)-dist(b.pos,toPos));
    return candidates;
  }
  // "las unidades no pueden regresar a un almacén que no tiene stock" — se elige el más cercano con stock
  function pickReturnWarehouse(fromPos){
    const candidates = Object.values(WAREHOUSES).filter(w => w.infinite || w.stock > 0);
    candidates.sort((a,b)=> dist(a.pos,fromPos)-dist(b.pos,fromPos));
    return candidates[0]; // el central (ilimitado) siempre califica
  }

  function tryAssign(){
    const pending = orders.filter(o=>o.status==='pending');
    for(const o of pending){
      const whCandidates = dispatchWarehouseCandidates(o.qty, o.pos);
      let wh = null, v = null;
      for(const w of whCandidates){
        const candidates = vehicles
          .filter(vv=>vv.state==='idle' && vv.home===w.id && vv.capacity>=o.qty)
          .sort((a,b)=>a.capacity-b.capacity);
        if(candidates.length){ wh = w; v = candidates[0]; break; }
      }
      if(!wh || !v) continue; // ningún almacén con stock tiene, por ahora, una unidad libre para este pedido
      v.state='toClient';
      v.order=o.id;
      v.path = computeRoute(v.pos, o.pos);
      v.pathIdx = 0;
      if(!wh.infinite) wh.stock -= o.qty;
      wh.dispatchedToday = (wh.dispatchedToday||0) + 1;
      o.status='assigned';
      o.vehicleId = v.id;
      o.warehouseId = wh.id;
      addLog(`Pedido <b>#${o.id}</b> (${o.clientId} · ${o.qty} uds.) asignado a <b>${v.id}</b> desde ${wh.name}.`, 'accent');
    }
  }

