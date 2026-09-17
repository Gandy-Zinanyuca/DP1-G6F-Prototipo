// Buscar unidad/almacén por código, filtrar el mapa por tipo de vehículo, mostrar/ocultar rutas, y el panel de búsqueda desplegable.
// Depende de: core/state.js, ui/render.js, ui/selection-panel.js
"use strict";
  /* =================== BUSCAR UNIDAD / FILTRO POR TIPO =================== */
  const vehSearch = document.getElementById('vehSearch');
  const vehSearchFound = document.getElementById('vehSearchFound');
  vehSearch.addEventListener('keydown', e=>{
    if(e.key!=='Enter') return;
    const code = normVehId(vehSearch.value);
    const v = vehicles.find(vv=>vv.id===code);
    if(!v){
      vehSearchFound.textContent = 'No se encontró esa unidad.';
      vehSearchFound.className = 'found err';
      return;
    }
    if(v.state==='idle' || v.state==='break'){
      vehSearchFound.textContent = `${v.id} está disponible en almacén (no se muestra en el mapa).`;
      vehSearchFound.className = 'found err';
      return;
    }
    selected = {type:'vehicle', id:v.id};
    refreshSelectionPanel();
    camera.scale = Math.max(camera.scale, 2.5);
    camera.cx = v.pos.x/GRID_W; camera.cy = 1 - v.pos.y/GRID_H;
    clampCamera();
    vehSearchFound.textContent = `${v.id} encontrada y centrada en el mapa.`;
    vehSearchFound.className = 'found ok';
  });

  document.querySelectorAll('.type-filters button').forEach(btn=>{
    btn.addEventListener('click', ()=>{
      const type = btn.dataset.type;
      typeFilter[type] = !typeFilter[type];
      btn.classList.toggle('active', typeFilter[type]);
    });
  });

  document.getElementById('toggleRoutes').addEventListener('click', function(){
    showRoutes = !showRoutes;
    this.classList.toggle('active', showRoutes);
    this.textContent = showRoutes ? '┄ Mostrar rutas asignadas' : '┄ Rutas ocultas';
  });

  // centra el mapa y abre el panel de detalle de un almacén — reutilizado por la búsqueda y por las tarjetas del sidebar
  function selectWarehouseAndCenter(wh){
    switchModule('mapa');
    selected = {type:'warehouse', id:wh.id};
    refreshSelectionPanel();
    camera.scale = Math.max(camera.scale, 2);
    camera.cx = wh.pos.x/GRID_W; camera.cy = 1 - wh.pos.y/GRID_H;
    clampCamera();
  }

  // centra el mapa y abre el panel de detalle de una unidad — reutilizado por las filas de Pedidos y de Averías;
  // no hace nada si la unidad ya no está activa en el mapa (entregó, volvió a idle, etc.)
  function selectVehicleAndCenter(vid){
    const v = vehicles.find(vv=>vv.id===vid);
    if(!v || v.state==='idle' || v.state==='break') return false;
    switchModule('mapa');
    selected = {type:'vehicle', id:v.id};
    camera.scale = Math.max(camera.scale, 2.5);
    camera.cx = v.pos.x/GRID_W; camera.cy = 1 - v.pos.y/GRID_H;
    clampCamera();
    refreshSelectionPanel();
    return true;
  }

  document.querySelectorAll('.wh-card[data-wh]').forEach(card=>{
    card.addEventListener('click', ()=>{
      const wh = WAREHOUSES[card.dataset.wh];
      if(wh) selectWarehouseAndCenter(wh);
    });
  });

  const whSearch = document.getElementById('whSearch');
  const whSearchFound = document.getElementById('whSearchFound');
  whSearch.addEventListener('keydown', e=>{
    if(e.key!=='Enter') return;
    const q = whSearch.value.trim().toLowerCase();
    // por prefijo primero (evita falsos positivos, ej. "este" no debe encontrar "Nor-Oeste")
    const whList = Object.values(WAREHOUSES);
    const wh = whList.find(w=> w.id.toLowerCase().startsWith(q) || w.name.replace('Almacén ','').toLowerCase().startsWith(q))
      || whList.find(w=> w.name.toLowerCase().includes(q));
    if(!wh){
      whSearchFound.textContent = 'No se encontró ese almacén.';
      whSearchFound.className = 'found err';
      return;
    }
    selectWarehouseAndCenter(wh);
    whSearchFound.textContent = `${wh.name} encontrado y centrado en el mapa.`;
    whSearchFound.className = 'found ok';
  });
