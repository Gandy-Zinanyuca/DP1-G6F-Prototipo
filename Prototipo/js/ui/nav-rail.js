// Rail lateral de navegación (Mapa + 5 módulos) que reemplaza el antiguo desplegable de módulo.
// Depende de: ui/*-module.js
"use strict";
  /* =================== NAVEGACIÓN: rail lateral (reemplaza el desplegable de módulo) =================== */
  const mainEl = document.querySelector('main');
  const navItems = document.querySelectorAll('.nav-item');
  const MODULE_PANELS = {
    pedidos: document.getElementById('modPedidos'),
    flota: document.getElementById('modFlota'),
    averias: document.getElementById('modAverias'),
    mantenimiento: document.getElementById('modMantenimiento'),
    incidencias: document.getElementById('modIncidencias'),
  };
  function switchModule(v){
    navItems.forEach(btn=> btn.classList.toggle('active', btn.dataset.mod===v));
    mainEl.classList.toggle('module-active', v!=='mapa');
    Object.entries(MODULE_PANELS).forEach(([key, el])=> el.classList.toggle('active', key===v));
    // al volver al mapa, su contenedor pudo haber estado oculto (display:none) la última vez que se midió —
    // se vuelve a medir para que el <canvas> nunca se quede en 0×0 y el mapa no aparezca en blanco
    if(v==='mapa') resizeCanvas();
    if(v==='incidencias') renderIncidenciasModule();
    if(v==='pedidos') renderPedidosModule();
    if(v==='flota') renderFlotaModule();
    if(v==='averias') renderAveriasModule();
    if(v==='mantenimiento') renderMantenimientoModule();
  }
  navItems.forEach(btn=> btn.addEventListener('click', ()=> switchModule(btn.dataset.mod)));
