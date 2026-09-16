// Tabs de escenario, Start/Stop, modal de inicio (escenario, flota, almacenes, turnos) y botón Reiniciar.
// Depende de: core/*, sim/sim-step.js
"use strict";
  /* =================== CONTROLES DE EJECUCIÓN: módulo, start/stop, modal de inicio =================== */
  const btnStartStop = document.getElementById('btnStartStop');
  const startStopIcon = document.getElementById('startStopIcon');
  const startStopLabel = document.getElementById('startStopLabel');
  const startGateModal = document.getElementById('startGateModal');
  const gateFecha = document.getElementById('gateFecha');
  const gateHora = document.getElementById('gateHora');
  const gateEscenario = document.getElementById('gateEscenario');
  const gateFechaRow = document.getElementById('gateFechaRow');
  const gateDesc = document.getElementById('gateDesc');
  const btnGateConfirm = document.getElementById('btnGateConfirm');

  const ESCENARIO_LABEL = { diaria:'Operación diaria', '5d':'Simulación 5 días', colapso:'Simulación hasta el colapso' };
  const ESCENARIO_DESC = {
    diaria: 'No requiere fecha: corre como operación en vivo. La simulación no avanza hasta que registres al menos un pedido en el módulo "Registro de pedidos".',
    '5d': 'Permite cargar un lote masivo de pedidos y bloqueos (botón "Datos de entrada"). Corre 5 días simulados y reinicia el ciclo.',
    colapso: 'Corre sin límite de días hasta que el primer pedido no se entregue dentro de su plazo — en ese instante la ejecución se detiene y se reporta el colapso.',
  };

  function updateGateFieldsVisibility(){
    const v = gateEscenario.value;
    gateFechaRow.style.display = v==='diaria' ? 'none' : 'flex';
    gateDesc.textContent = ESCENARIO_DESC[v];
    btnGateConfirm.textContent = v==='diaria' ? 'Continuar a registro de pedidos' : 'Iniciar simulación';
  }
  gateEscenario.addEventListener('change', updateGateFieldsVisibility);
  updateGateFieldsVisibility();

  // banner de estado, bien visible arriba de la pantalla — no depende de abrir el panel de indicadores
  const toastBanner = document.getElementById('toastBanner');
  let toastTimer = null;
  function showToast(msg, kind){
    toastBanner.textContent = msg;
    toastBanner.className = 'toast-banner show' + (kind ? ' '+kind : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(()=>{ toastBanner.classList.remove('show'); }, 3200);
  }

  function beginRun(){
    startedAtReal = performance.now();
    running = true;
    btnStartStop.classList.add('running');
    startStopIcon.textContent = '⏸'; startStopLabel.textContent = 'Detener';
    lastTs = null;
    addLog('Ejecución <b>iniciada</b>.', 'good');
    showToast('▶ Ejecución iniciada', 'good');
  }
  function stopRun(reason, kind){
    running = false;
    accumulatedRunMs += performance.now() - startedAtReal;
    btnStartStop.classList.remove('running');
    startStopIcon.textContent = '▶'; startStopLabel.textContent = 'Iniciar';
    addLog(reason || 'Ejecución <b>detenida</b>.', 'warning');
    showToast('■ Ejecución detenida', kind || 'warning');
    lastTs = null;
  }

  document.getElementById('btnGateConfirm').addEventListener('click', ()=>{
    scenario = gateEscenario.value;
    gateConfirmed = true;
    updateScenarioTabsUI();
    startGateModal.classList.remove('open');

    fleetConfig = {
      auto: Math.max(0, parseInt(document.getElementById('gateAutos').value,10) || 0),
      moto: Math.max(0, parseInt(document.getElementById('gateMotos').value,10) || 0),
      bici: Math.max(0, parseInt(document.getElementById('gateBicis').value,10) || 0),
    };

    WAREHOUSES.noroeste.capacity = Math.max(100, parseInt(document.getElementById('gateCapNoroeste').value,10) || 1000);
    WAREHOUSES.este.capacity = Math.max(100, parseInt(document.getElementById('gateCapEste').value,10) || 1000);

    const parseHM = (id, fallback)=>{
      const v = document.getElementById(id).value;
      if(!v) return fallback;
      const [hh,mi] = v.split(':').map(Number);
      return Number.isFinite(hh) && Number.isFinite(mi) ? hh*60+mi : fallback;
    };
    shiftStarts = [
      parseHM('gateTurno1', 7*60),
      parseHM('gateTurno2', 15*60),
      parseHM('gateTurno3', 23*60),
    ];
    SHIFTS = buildShifts();

    if(scenario==='diaria'){
      simEpochDate = new Date();
      resetSimulation(7*60);
      diariaWaiting = true;
      addLog(`Escenario <b>Operación diaria</b> configurado. Registra un pedido en el módulo "Registro de pedidos" para que la operación comience.`, 'accent');
      switchModule('pedidos');
      return;
    }

    const [y,m,d] = gateFecha.value.split('-').map(Number);
    const [hh,mi] = gateHora.value.split(':').map(Number);
    simEpochDate = new Date(y, m-1, d);
    colapsoTriggered = false;
    resetSimulation(hh*60 + mi);
    addLog(`Ejecución configurada: inicio ${gateFecha.value} ${gateHora.value}, escenario <b>${ESCENARIO_LABEL[scenario]}</b>.`, 'accent');
    switchModule('mapa');
    beginRun();
  });

  btnStartStop.addEventListener('click', ()=>{
    if(!gateConfirmed){ startGateModal.classList.add('open'); return; }
    if(scenario==='diaria' && diariaWaiting){
      addLog('Aún no hay pedidos registrados — la operación diaria no puede comenzar hasta el primer registro.', 'warning');
      switchModule('pedidos');
      return;
    }
    if(running) stopRun('Ejecución <b>detenida</b> por el usuario.'); else beginRun();
  });

  /* =================== REINICIAR: detiene todo y vuelve al modal de inicio =================== */
  const btnReiniciar = document.getElementById('btnReiniciar');
  const resetConfirmModal = document.getElementById('resetConfirmModal');
  let pendingScenarioAfterReset = null; // escenario a preseleccionar en el modal de inicio tras un reinicio pedido desde las tabs

  function openResetConfirm(targetEscenario){
    pendingScenarioAfterReset = targetEscenario || null;
    resetConfirmModal.classList.add('open');
  }
  btnReiniciar.addEventListener('click', ()=> openResetConfirm(null));
  document.getElementById('btnResetCancel').addEventListener('click', ()=>{
    pendingScenarioAfterReset = null;
    resetConfirmModal.classList.remove('open');
  });
  document.getElementById('btnResetOk').addEventListener('click', ()=>{
    resetConfirmModal.classList.remove('open');
    if(running) stopRun('Ejecución detenida para reiniciar.');
    gateConfirmed = false;
    diariaWaiting = false;
    colapsoTriggered = false;
    accumulatedRunMs = 0;
    updateScenarioTabsUI();
    switchModule('mapa');
    addLog('Simulación <b>reiniciada</b> — elige de nuevo el escenario, la flota y la fecha de inicio.', 'warning');
    if(pendingScenarioAfterReset){
      gateEscenario.value = pendingScenarioAfterReset;
      updateGateFieldsVisibility();
      pendingScenarioAfterReset = null;
    }
    startGateModal.classList.add('open');
  });

  /* =================== TABS DE ESCENARIO: siempre visibles, reemplazan la elección única del modal =================== */
  const scenarioTabEls = document.querySelectorAll('.scenario-tab');
  function updateScenarioTabsUI(){
    scenarioTabEls.forEach(btn=> btn.classList.toggle('active', gateConfirmed && btn.dataset.esc===scenario));
  }
  scenarioTabEls.forEach(btn=>{
    btn.addEventListener('click', ()=>{
      const target = btn.dataset.esc;
      if(!gateConfirmed){
        gateEscenario.value = target;
        updateGateFieldsVisibility();
        startGateModal.classList.add('open');
        return;
      }
      if(target===scenario) return; // ya es el escenario activo
      openResetConfirm(target);
    });
  });
  updateScenarioTabsUI();
