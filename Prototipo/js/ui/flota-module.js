// Módulo Flota: tabla completa de la flota configurada, su estado, carga actual y últimos pedidos.
// Depende de: core/state.js, ui/selection-panel.js
"use strict";
  /* =================== MÓDULO: FLOTA =================== */
  const flotaTbody = document.getElementById('flotaTbody');
  function renderFlotaModule(){
    if(!vehicles.length){
      flotaTbody.innerHTML = `<tr><td colspan="5" class="inc-empty">La flota se configura en el modal de inicio.</td></tr>`;
      return;
    }
    flotaTbody.innerHTML = vehicles.map(v=>{
      const order = v.order ? orders.find(o=>o.id===v.order) : null;
      const historial = orderHistory.filter(o=>o.vehicleId===v.id).slice(0,3).map(o=>'#'+o.id).join(', ');
      return `<tr>
        <td>${v.id}</td>
        <td>${VEHICLE_TYPES[v.type].label}</td>
        <td>${vehicleStateLabel(v)}</td>
        <td>${order ? '#'+order.id : '—'}</td>
        <td>${order ? `${order.qty} / ${v.capacity}` : `0 / ${v.capacity}`}</td>
        <td>${historial || '—'}</td>
        <td>(${Math.round(v.pos.x)}, ${Math.round(v.pos.y)})</td>
      </tr>`;
    }).join('');
  }
