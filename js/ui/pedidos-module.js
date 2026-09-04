// Módulo de registro de pedidos: alta manual, carga masiva, sus modales de confirmación y la tabla filtrable.
// Depende de: sim/orders.js, core/state.js
"use strict";
  /* =================== MÓDULO: REGISTRO DE PEDIDOS =================== */
  const pedModalidad = document.getElementById('pedModalidad');
  const PEDIDO_MODALIDADES = [ {v:36,l:'Regular (36 h)'}, {v:18,l:'Priorizada (18 h)'}, {v:12,l:'Priorizada (12 h)'}, {v:8,l:'Priorizada (8 h)'}, {v:4,l:'Priorizada (4 h)'} ];
  PEDIDO_MODALIDADES.forEach(m=>{ const o=document.createElement('option'); o.value=m.v; o.textContent=m.l; pedModalidad.appendChild(o); });

  const pedAlert = document.getElementById('pedAlert');
  function pedError(msg){ pedAlert.textContent = msg; pedAlert.style.display = 'block'; }
  function pedClearError(){ pedAlert.style.display = 'none'; }

  // valida y registra un pedido; retorna {ok, motivo} — usado por el alta manual y por la carga masiva
  function validarYRegistrarPedido({clientId, qty, hourLimit, x, y}){
    if(!clientId) return {ok:false, motivo:'cliente vacío'};
    if(!Number.isInteger(qty) || qty<1 || qty>24) return {ok:false, motivo:`cantidad fuera de rango (${qty})`};
    if(!Number.isInteger(x) || !Number.isInteger(y) || x<0 || x>GRID_W || y<0 || y>GRID_H) return {ok:false, motivo:'destino fuera de la retícula'};
    if(![36,18,12,8,4].includes(hourLimit)) return {ok:false, motivo:'modalidad no reconocida'};

    let enRiesgo = false;
    const distDirecta = Math.hypot(x-25, y-15); // referencia: distancia desde el almacén central
    const horasTraslado = distDirecta / VEHICLE_TYPES.auto.speed;
    if(hourLimit<36 && horasTraslado>=hourLimit) enRiesgo = true; // LE010: se marca en riesgo, no se rechaza

    orders.push({
      id: orderSeq++, clientId, pos:{x,y}, qty, priority:hourLimit,
      createdAt: simMin, deadline: simMin+hourLimit*60, status:'pending', enRiesgo,
    });
    if(scenario==='diaria' && diariaWaiting){
      diariaWaiting = false;
      beginRun();
      addLog('Primer pedido registrado — la operación diaria comienza a correr.', 'good');
    }
    return {ok:true, enRiesgo};
  }

  // --- confirmación previa a registrar UN pedido ---
  let pedidoPendiente = null;
  const pedConfirmModal = document.getElementById('pedConfirmModal');

  document.getElementById('btnRegistrarPedido').addEventListener('click', ()=>{
    pedClearError();
    const data = {
      clientId: document.getElementById('pedCliente').value.trim(),
      qty: parseInt(document.getElementById('pedCantidad').value, 10),
      hourLimit: +pedModalidad.value,
      x: parseInt(document.getElementById('pedX').value, 10),
      y: parseInt(document.getElementById('pedY').value, 10),
    };
    if(!data.clientId || !Number.isFinite(data.qty) || !Number.isFinite(data.x) || !Number.isFinite(data.y)){
      pedError('Completa cliente, cantidad y destino antes de registrar.');
      return;
    }
    pedidoPendiente = data;
    document.getElementById('pcCliente').textContent = data.clientId;
    document.getElementById('pcCantidad').textContent = data.qty + ' paquete(s)';
    document.getElementById('pcModalidad').textContent = (PEDIDO_MODALIDADES.find(m=>m.v===data.hourLimit)||{}).l || (data.hourLimit+' h');
    document.getElementById('pcDestino').textContent = `(${data.x}, ${data.y}) km`;
    pedConfirmModal.classList.add('open');
  });

  document.getElementById('btnPedConfirmCancel').addEventListener('click', ()=>{
    pedidoPendiente = null;
    pedConfirmModal.classList.remove('open');
  });

  document.getElementById('btnPedConfirmOk').addEventListener('click', ()=>{
    if(!pedidoPendiente) return;
    const res = validarYRegistrarPedido(pedidoPendiente);
    pedidoPendiente = null;
    pedConfirmModal.classList.remove('open');
    if(!res.ok){ pedError('No se pudo registrar: ' + res.motivo + '.'); return; }
    if(res.enRiesgo) addLog('Pedido registrado, pero marcado <b>en riesgo</b>: el plazo elegido podría no ser alcanzable.', 'warning');
    document.getElementById('pedCliente').value='';
    document.getElementById('pedCantidad').value='';
    document.getElementById('pedX').value=''; document.getElementById('pedY').value='';
    renderPedidosModule();
  });

  // --- confirmación previa a cargar un LOTE de pedidos ---
  let masivoPendiente = null;
  const masivoConfirmModal = document.getElementById('masivoConfirmModal');

  document.getElementById('btnRegistrarMasivo').addEventListener('click', ()=>{
    const lines = document.getElementById('pedMasivoTexto').value.split(/\r?\n/).map(l=>l.trim()).filter(Boolean);
    if(!lines.length){ document.getElementById('masivoResultado').textContent = 'No hay líneas para cargar.'; return; }
    masivoPendiente = lines;
    document.getElementById('mcCantidad').textContent = lines.length;
    const preview = lines.slice(0,6).map(l=>l.replace(/</g,'&lt;')).join('<br>');
    document.getElementById('mcPreview').innerHTML = preview + (lines.length>6 ? `<br>… y ${lines.length-6} línea(s) más` : '');
    masivoConfirmModal.classList.add('open');
  });

  document.getElementById('btnMasivoConfirmCancel').addEventListener('click', ()=>{
    masivoPendiente = null;
    masivoConfirmModal.classList.remove('open');
  });

  document.getElementById('btnMasivoConfirmOk').addEventListener('click', ()=>{
    if(!masivoPendiente) return;
    const lines = masivoPendiente;
    masivoPendiente = null;
    masivoConfirmModal.classList.remove('open');
    let ok=0, fail=0; const report=[];
    lines.forEach((line,i)=>{
      const parts = line.split(',').map(s=>s.trim());
      if(parts.length<5){ fail++; report.push(`Fila ${i+1}: formato inválido.`); return; }
      const [clientId, qtyS, hlS, xS, yS] = parts;
      const res = validarYRegistrarPedido({ clientId, qty:parseInt(qtyS,10), hourLimit:parseInt(hlS,10), x:parseInt(xS,10), y:parseInt(yS,10) });
      if(res.ok){ ok++; } else { fail++; report.push(`Fila ${i+1} (${clientId||'sin cliente'}): ${res.motivo}.`); }
    });
    document.getElementById('masivoResultado').innerHTML = lines.length
      ? `<b>${ok}</b> pedidos cargados, <b>${fail}</b> rechazados.` + (report.length ? '<br>'+report.join('<br>') : '')
      : '';
    if(ok) addLog(`Carga masiva de pedidos (interfaz): ${ok} aceptados, ${fail} rechazados.`, 'accent');
    document.getElementById('pedMasivoTexto').value='';
    renderPedidosModule();
  });

  document.getElementById('pedFiltroEstado').addEventListener('change', renderPedidosModule);
  document.getElementById('pedFiltroModalidad').addEventListener('change', renderPedidosModule);
  document.getElementById('pedBuscarCliente').addEventListener('input', renderPedidosModule);

  function estadoDeOrder(o){
    if(o.status==='pending') return 'registrado';
    if(o.status==='assigned') return 'en ruta';
    return o.status; // 'entregado' | 'no cumplido' (solo aplica a orderHistory)
  }

  function renderPedidosModule(){
    document.getElementById('pedidosMsub').textContent = scenario==='diaria' && diariaWaiting
      ? 'La operación diaria está en espera del primer pedido — regístralo abajo para que comience a correr.'
      : 'Registro manual y masivo de pedidos, con las mismas validaciones que aplica la simulación.';

    const fe = document.getElementById('pedFiltroEstado').value;
    const fm = document.getElementById('pedFiltroModalidad').value;
    const fc = document.getElementById('pedBuscarCliente').value.trim().toLowerCase();
    const live = orders.map(o=>({id:o.id, clientId:o.clientId, qty:o.qty, priority:o.priority, pos:o.pos, deadline:o.deadline, estado:estadoDeOrder(o)}));
    const hist = orderHistory.map(o=>({id:o.id, clientId:o.clientId, qty:o.qty, priority:o.priority, pos:o.pos, deadline:o.deadline, estado:o.estadoFinal}));
    let rows = live.concat(hist).sort((a,b)=>b.id-a.id);
    if(fe) rows = rows.filter(r=>r.estado===fe);
    if(fm) rows = rows.filter(r=>String(r.priority)===fm);
    if(fc) rows = rows.filter(r=>r.clientId.toLowerCase().includes(fc));

    const tbody = document.getElementById('pedTbody');
    if(!rows.length){ tbody.innerHTML = `<tr><td colspan="7" class="inc-empty">Sin pedidos que coincidan con el filtro.</td></tr>`; return; }
    const badgeClass = { 'registrado':'good', 'en ruta':'warning', 'entregado':'good', 'no cumplido':'critical' };
    tbody.innerHTML = rows.slice(0,200).map(r=>`
      <tr><td>#${r.id}</td><td>${r.clientId}</td><td>${r.qty}</td>
      <td>${r.priority<36?'Priorizada '+r.priority+'h':'Regular 36h'}</td>
      <td>(${r.pos.x},${r.pos.y})</td><td>${fmtTime(r.deadline)}</td>
      <td><span class="legend-live" style="color:var(--${badgeClass[r.estado]||'ink-3'})">${r.estado}</span></td></tr>
    `).join('');
  }

