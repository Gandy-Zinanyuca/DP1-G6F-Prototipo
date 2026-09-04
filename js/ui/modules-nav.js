// Desplegable de módulo (mapa / pedidos / incidencias) en la cabecera.
// Depende de: ui/incidencias-module.js, ui/pedidos-module.js
"use strict";
  /* =================== MÓDULOS: desplegable para cambiar de vista (mapa / pedidos / incidencias) =================== */
  const mainEl = document.querySelector('main');
  const modIncidencias = document.getElementById('modIncidencias');
  const modPedidos = document.getElementById('modPedidos');
  function switchModule(v){
    modSelect.value = v;
    mainEl.classList.toggle('module-active', v!=='mapa');
    modIncidencias.classList.toggle('active', v==='incidencias');
    modPedidos.classList.toggle('active', v==='pedidos');
    if(v==='incidencias') renderIncidenciasModule();
    if(v==='pedidos') renderPedidosModule();
  }
  modSelect.addEventListener('change', ()=> switchModule(modSelect.value));

