// Panel de detalle al hacer clic en un vehículo/almacén/bloqueo sobre el mapa, y los helpers de selección compartidos con las tablas de los módulos.
// Depende de: core/*, ui/render.js
"use strict";
  /* =================== SELECTION / DETAIL PANEL =================== */
  const selectionPanel = document.getElementById('selectionPanel');
  const selSwatch = document.getElementById('selSwatch');
  const selName = document.getElementById('selName');
  const selSub = document.getElementById('selSub');
  const selBody = document.getElementById('selBody');
  document.getElementById('selClose').addEventListener('click', ()=>{ selected=null; hideSelection(); });
  selBody.addEventListener('click', e=>{
    const btn = e.target.closest('button[data-act="registrar-averia"]');
    if(!btn) return;
    const tipo = +document.getElementById('selAveriaTipo').value;
    triggerAveriaManual(btn.dataset.id, tipo);
    refreshSelectionPanel();
  });

  function hideSelection(){ selectionPanel.classList.remove('open'); }

  function remainingDistance(v){
    if(!v.path) return 0;
    let remain = 0;
    if(v.pathIdx < v.path.length-1) remain += dist(v.pos, v.path[v.pathIdx+1]);
    for(let i=v.pathIdx+1; i<v.path.length-1; i++) remain += dist(v.path[i], v.path[i+1]);
    return remain;
  }
  function etaText(v){
    if(v.state==='atClient') return `Entregando (${Math.max(0,Math.round(v.timer))} min)`;
    if(!v.path) return '—';
    const mins = remainingDistance(v)/v.speed*60;
    return `${Math.round(mins)} min · llega ${fmtTime(simMin+mins)}`;
  }
  function vehicleStateLabel(v){
    switch(v.state){
      case 'toClient': return 'En ruta al cliente';
      case 'atClient': return 'Entregando en destino';
      case 'returning': return 'Retornando a recargar';
      case 'broken': return 'Avería — en espera de reasignación';
      case 'maintenance': return 'En mantenimiento';
      default: return 'Disponible';
    }
  }

  function renderVehicleDetail(v){
    const t = VEHICLE_TYPES[v.type];
    selSwatch.style.background = css(t.color);
    selSwatch.style.borderRadius = '3px';
    selName.textContent = `${t.label} ${v.id}`;
    selSub.textContent = vehicleStateLabel(v);
    const order = v.order ? orders.find(o=>o.id===v.order) : null;
    let rows = `<div class="sel-row"><span>Capacidad</span><span>${t.capacity} paq.</span></div>
      <div class="sel-row"><span>Velocidad</span><span>${t.speed} km/h</span></div>
      <div class="sel-row"><span>Tarifa</span><span>S/ ${t.cost.toFixed(2)}/km</span></div>
      <div class="sel-row"><span>Posición</span><span>(${Math.round(v.pos.x)}, ${Math.round(v.pos.y)})</span></div>`;
    if(v.state==='broken'){
      const inc = incidents.find(i=>i.type==='falla' && i.vehicleId===v.id);
      rows += `<div class="sel-row"><span>Tipo de avería</span><span>${inc ? inc.falla.label : '—'}</span></div>
        <div class="sel-row"><span>Se repara</span><span>${inc ? fmtTime(inc.until) : '—'}</span></div>`;
    }
    if(v.state==='maintenance'){
      const inc = incidents.find(i=>i.type==='mantenimiento' && i.vehicleId===v.id);
      rows += `<div class="sel-row"><span>Mantenimiento</span><span>${inc ? inc.horas+' h' : '—'}</span></div>
        <div class="sel-row"><span>Disponible desde</span><span>${inc ? fmtTime(inc.until) : '—'}</span></div>`;
    }
    if(order){
      rows += `<div class="sel-row"><span>Pedido</span><span>#${order.id}</span></div>
        <div class="sel-row"><span>Cliente</span><span>${order.clientId} (${order.pos.x},${order.pos.y})</span></div>
        <div class="sel-row"><span>Carga</span><span>${order.qty}/${t.capacity} paq.</span></div>
        <div class="sel-row"><span>Prioridad</span><span>${order.priority} h</span></div>
        <div class="sel-row"><span>ETA</span><span>${etaText(v)}</span></div>`;
    } else if(v.state==='returning'){
      rows += `<div class="sel-row"><span>Regreso a</span><span>${WAREHOUSES[v.returnTarget||v.home].name}</span></div>
        <div class="sel-row"><span>ETA</span><span>${etaText(v)}</span></div>`;
    } else {
      rows += `<div class="sel-row"><span>Base actual</span><span>${WAREHOUSES[v.home].name}</span></div>`;
    }
    if(v.state==='toClient' || v.state==='returning'){
      rows += `<div class="sel-actions">
        <select id="selAveriaTipo">
          ${FALLA_TYPES.map(f=>`<option value="${f.tipo}">Avería ${f.label}</option>`).join('')}
        </select>
        <button data-act="registrar-averia" data-id="${v.id}">Registrar</button>
      </div>`;
    }
    selBody.innerHTML = rows;
  }

  function renderWarehouseDetail(wh){
    selSwatch.style.background = css('--accent');
    selSwatch.style.borderRadius = '50%';
    selName.textContent = wh.name;
    selSub.textContent = wh.infinite ? 'Almacén central' : 'Almacén intermedio';
    const homeCount = vehicles.filter(v=>v.home===wh.id).length;
    let rows = `<div class="sel-row"><span>Nodo</span><span>(${wh.pos.x}, ${wh.pos.y})</span></div>`;
    rows += wh.infinite
      ? `<div class="sel-row"><span>Stock</span><span>Ilimitado</span></div>`
      : `<div class="sel-row"><span>Stock</span><span>${wh.stock} / ${wh.capacity}</span></div>
         <div class="sel-row"><span>Ocupación</span><span>${Math.round(wh.stock/wh.capacity*100)}%</span></div>`;
    rows += `<div class="sel-row"><span>Vehículos con base aquí</span><span>${homeCount}</span></div>
      <div class="sel-row"><span>Despachados hoy</span><span>${wh.dispatchedToday||0}</span></div>`;
    selBody.innerHTML = rows;

    // listas de detalle: pedidos que salen, unidades que salen y unidades que arriban a este almacén
    const salenPedidos = orders.filter(o=>o.warehouseId===wh.id && o.status==='assigned');
    const salenUnidades = vehicles.filter(v=>v.home===wh.id && v.state==='toClient');
    const arribanUnidades = vehicles.filter(v=>v.state==='returning' && v.returnTarget===wh.id);
    const listHtml = (title, items, renderItem)=> !items.length ? '' : `
      <div class="sel-row" style="border-top:1px solid var(--border);flex-direction:column;align-items:flex-start;gap:4px;padding-top:8px;">
        <span style="font-weight:600;color:var(--ink);">${title} (${items.length})</span>
        <div style="width:100%;display:flex;flex-direction:column;gap:2px;font-family:'IBM Plex Mono',monospace;font-size:11px;color:var(--ink-2);">
          ${items.slice(0,6).map(renderItem).join('')}
        </div>
      </div>`;
    selBody.innerHTML += listHtml('📦 Pedidos que salen', salenPedidos, o=>`#${o.id} · ${o.clientId} · ${o.qty} uds.`);
    selBody.innerHTML += listHtml('🚚 Unidades que salen', salenUnidades, v=>`${v.id} (${VEHICLE_TYPES[v.type].label})`);
    selBody.innerHTML += listHtml('↩️ Unidades que arriban', arribanUnidades, v=>`${v.id} (${VEHICLE_TYPES[v.type].label}) — ${etaText(v)}`);
  }

  function renderBloqueoDetail(inc){
    selSwatch.style.background = css('--critical');
    selSwatch.style.borderRadius = '3px';
    selName.textContent = '🚧 Bloqueo';
    const a = inc.nodes[0], b = inc.nodes[inc.nodes.length-1];
    selSub.textContent = `(${a.x},${a.y}) → (${b.x},${b.y})`;
    const restante = Math.max(0, inc.until - simMin);
    selBody.innerHTML = `<div class="sel-row"><span>Tramos afectados</span><span>${inc.nodes.length-1}</span></div>
      <div class="sel-row"><span>Desde</span><span>${fmtTime(inc.since)}</span></div>
      <div class="sel-row"><span>Hasta</span><span>${fmtTime(inc.until)}</span></div>
      <div class="sel-row"><span>Se despeja en</span><span>${Math.round(restante)} min</span></div>
      <div class="sel-row"><span>Origen</span><span>${inc.fromFile ? 'archivo de bloqueos' : 'registro manual / aleatorio'}</span></div>`;
  }

  function renderZoneDetail(name){
    selSwatch.style.background = css('--ink-3');
    selSwatch.style.borderRadius = '50%';
    selName.textContent = name;
    selSub.textContent = 'Sector de la retícula';
    const activeHere = orders.filter(o=>sectorOf(o.pos)===name && o.status!=='delivered').length;
    const d = stats.bySector[name] || {delivered:0, onTime:0};
    const pct = d.delivered ? Math.round(d.onTime/d.delivered*100) : null;
    selBody.innerHTML = `<div class="sel-row"><span>Pedidos activos</span><span>${activeHere}</span></div>
      <div class="sel-row"><span>Entregados (ciclo)</span><span>${d.delivered}</span></div>
      <div class="sel-row"><span>A tiempo</span><span>${pct===null?'—':pct+'%'}</span></div>`;
  }

  function refreshSelectionPanel(){
    if(!selected) return;
    if(selected.type==='vehicle'){
      const v = vehicles.find(vv=>vv.id===selected.id);
      if(!v || v.state==='idle' || v.state==='break'){ selected=null; hideSelection(); return; }
      renderVehicleDetail(v);
    } else if(selected.type==='warehouse'){
      renderWarehouseDetail(WAREHOUSES[selected.id]);
    } else if(selected.type==='bloqueo'){
      if(!incidents.includes(selected.ref)){ selected=null; hideSelection(); return; }
      renderBloqueoDetail(selected.ref);
    } else if(selected.type==='zone'){
      renderZoneDetail(selected.name);
    }
    selectionPanel.classList.add('open');
  }

  function handleMapClick(sx, sy){
    let hit=null;
    for(const v of vehicles){
      if(v.state==='idle' || v.state==='break' || !typeFilter[v.type]) continue;
      const p = S(v.pos);
      if(Math.hypot(p.x-sx,p.y-sy) < 13){ hit={type:'vehicle', id:v.id}; break; }
    }
    if(!hit){
      for(const wh of Object.values(WAREHOUSES)){
        const p = S(wh.pos);
        const r = wh.infinite ? 11 : 9.5;
        const headY = p.y - r*1.7;
        if(Math.hypot(p.x-sx, headY-sy) < r+5){ hit={type:'warehouse', id:wh.id}; break; }
      }
    }
    if(!hit){
      for(const inc of incidents){
        if(inc.type!=='bloqueo') continue;
        for(let i=0;i<inc.nodes.length-1;i++){
          const a = S(inc.nodes[i]), b = S(inc.nodes[i+1]);
          if(distToSegment(sx,sy, a.x,a.y, b.x,b.y) < 9){ hit={type:'bloqueo', ref:inc}; break; }
        }
        if(hit) break;
      }
    }
    if(!hit){
      const world = screenToWorld(sx, sy);
      if(world.x>=0 && world.x<=GRID_W && world.y>=0 && world.y<=GRID_H){
        hit = {type:'zone', name: sectorOf(world)};
      }
    }
    if(hit){ selected = hit; refreshSelectionPanel(); } else { selected=null; hideSelection(); }
  }
  // distancia de un punto a un segmento (para el hit-test de bloqueos en el mapa)
  function distToSegment(px,py, ax,ay, bx,by){
    const dx=bx-ax, dy=by-ay;
    const len2 = dx*dx+dy*dy;
    let t = len2 ? ((px-ax)*dx+(py-ay)*dy)/len2 : 0;
    t = Math.max(0, Math.min(1, t));
    const cx = ax+t*dx, cy = ay+t*dy;
    return Math.hypot(px-cx, py-cy);
  }
