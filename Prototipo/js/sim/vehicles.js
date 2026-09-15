// Movimiento de vehículos a lo largo de su ruta, cambios de estado (idle/toClient/atClient/returning/broken).
// Depende de: core/*, core/grid.js, sim/orders.js
"use strict";
  /* =================== VEHICLE MOVEMENT =================== */
  function advanceVehicle(v, dtMin){
    if(v.state==='idle') return;
    if(v.state==='broken') return;

    if(v.state==='atClient'){
      // "se congela" una hora en el cliente antes de quedar libre para el siguiente pedido
      v.timer -= dtMin;
      if(v.timer <= 0){
        const order = orders.find(o=>o.id===v.order);
        if(order){
          order.status='delivered';
          const onTime = simMin <= order.deadline;
          stats.deliveredTotal++; stats.deliveredToday++;
          if(onTime) stats.onTime++; else stats.late++;
          const bucket = stats.byPriority[order.priority];
          if(bucket){ bucket.delivered++; if(onTime) bucket.onTime++; }
          const sKey = sectorOf(order.pos);
          if(!stats.bySector[sKey]) stats.bySector[sKey] = {delivered:0, onTime:0};
          stats.bySector[sKey].delivered++;
          if(onTime) stats.bySector[sKey].onTime++;
          flashes.push({pos:{...v.pos}, color: onTime? css('--good') : css('--critical'), born: simMin});
          addLog(`Pedido <b>#${order.id}</b> entregado ${onTime?'a tiempo':'con retraso'} por ${v.id} a ${order.clientId}.`, onTime?'good':'critical');
          orderHistory.unshift({...order, estadoFinal: onTime?'entregado':'no cumplido'});
          if(orderHistory.length>300) orderHistory.length=300;
          orders = orders.filter(o=>o.id!==order.id);

          if(!onTime && scenario==='colapso' && !colapsoTriggered){
            colapsoTriggered = true;
            const pendientes = orders.length;
            stopRun(`<b>Colapso logístico</b>: el pedido #${order.id} (nodo ${order.pos.x},${order.pos.y}) no se entregó dentro de su plazo. ${pendientes} pedido(s) quedaron sin atender. Ejecución detenida.`);
          }
        }
        const wh = pickReturnWarehouse(v.pos);
        v.state='returning';
        v.path = computeRoute(v.pos, wh.pos);
        v.pathIdx = 0;
        v.returnTarget = wh.id;
        v.trail = [];
      }
      return;
    }

    if(!v.path) return;
    let remaining = v.speed * (dtMin/60); // km disponibles este tick

    while(remaining > 0 && v.pathIdx < v.path.length-1){
      const target = v.path[v.pathIdx+1];
      const d = dist(v.pos, target);
      if(d <= remaining){
        v.pos = {...target};
        v.pathIdx++;
        remaining -= d;
        if(v.pathIdx === v.path.length-1){
          const segCost = pathLength(v.path) * v.costPerKm;
          stats.cost += segCost;
          if(v.state==='toClient'){
            v.state='atClient'; v.timer=60; v.heading = Math.atan2(target.y-v.pos.y, target.x-v.pos.x);
            v.trail = [];
          } else if(v.state==='returning'){
            v.state='idle'; v.order=null; v.path=null;
            v.home = v.returnTarget || v.home;
            v.trail = [];
          }
          break;
        }
      } else {
        const ang = Math.atan2(target.y-v.pos.y, target.x-v.pos.x);
        v.heading = ang;
        v.pos = { x: v.pos.x + Math.cos(ang)*remaining, y: v.pos.y + Math.sin(ang)*remaining };
        remaining = 0;
      }
      v.trail.push({x:v.pos.x, y:v.pos.y});
      if(v.trail.length > 18) v.trail.shift();
    }
  }

