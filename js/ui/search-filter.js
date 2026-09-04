// Buscar unidad por código y filtrar el mapa por tipo de vehículo.
// Depende de: core/state.js, ui/render.js
"use strict";
  /* =================== BUSCAR UNIDAD / FILTRO POR TIPO =================== */
  const vehSearch = document.getElementById('vehSearch');
  const vehSearchFound = document.getElementById('vehSearchFound');
  vehSearch.addEventListener('keydown', e=>{
    if(e.key!=='Enter') return;
    const code = vehSearch.value.trim().toUpperCase();
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

  /* =================== PANEL LATERAL / BUSCAR: desplegables ---------- */
  const sidebarPanel = document.getElementById('sidebarPanel');
  document.getElementById('sidebarToggle').addEventListener('click', e=>{
    e.stopPropagation();
    sidebarPanel.classList.toggle('open');
    document.getElementById('searchCtl').classList.remove('open');
  });
  document.getElementById('sidebarClose').addEventListener('click', ()=> sidebarPanel.classList.remove('open'));

  const searchCtl = document.getElementById('searchCtl');
  document.getElementById('searchToggle').addEventListener('click', e=>{
    e.stopPropagation();
    searchCtl.classList.toggle('open');
    sidebarPanel.classList.remove('open');
  });
  document.addEventListener('click', e=>{
    if(sidebarPanel.classList.contains('open') && !sidebarPanel.contains(e.target) && e.target.id!=='sidebarToggle'){
      sidebarPanel.classList.remove('open');
    }
    if(searchCtl.classList.contains('open') && !searchCtl.contains(e.target) && e.target.id!=='searchToggle'){
      searchCtl.classList.remove('open');
    }
  });

