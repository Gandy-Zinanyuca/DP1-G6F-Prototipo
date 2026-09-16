// Orquesta un paso de simulación (simStep) y resetSimulation() al confirmar el modal de inicio.
// Depende de: sim/*, core/*
"use strict";
  /* =================== MAIN SIM STEP =================== */
  let lastDayNum = 1;
  function simStep(dtMin){
    simMin += dtMin;
    cycleDay = Math.floor(simMin/1440)+1;
    if(cycleDay !== lastDayNum){
      stats.deliveredToday = 0;
      Object.values(WAREHOUSES).forEach(w=>w.dispatchedToday=0);
      lastDayNum = cycleDay;
    }
    if(scenario==='5d' && cycleDay > CYCLE_DAYS_5D){
      addLog(`Simulación 5D completada — reiniciando ciclo.`, 'accent');
      resetSimulation();
      return;
    }

    maybeSpawnOrder(dtMin);
    tryAssign();
    maybeTriggerIncident(dtMin);
    updateIncidents();
    applyMealBreaks();
    handleRestock();
    vehicles.forEach(v=>advanceVehicle(v, dtMin));
  }

  function resetSimulation(startMin){
    simMin = startMin!==undefined ? startMin : 7*60;
    runStartSimMin = simMin;
    cycleDay = 1; lastDayNum = 1; lastRestockDay = -1;
    orders = []; flashes = []; incidents = []; blockedEdges = new Set(); orderHistory = [];
    stats = { deliveredTotal:0, deliveredToday:0, onTime:0, late:0, cost:0, byPriority:{}, bySector:{} };
    resetPriorityStats();
    // stock inicial: mismo porcentaje de ocupación que el escenario por defecto (76% / 84%), sobre la capacidad configurada
    WAREHOUSES.noroeste.stock = Math.round(WAREHOUSES.noroeste.capacity * 0.76);
    WAREHOUSES.este.stock = Math.round(WAREHOUSES.este.capacity * 0.84);
    Object.values(WAREHOUSES).forEach(w=>w.dispatchedToday=0);
    ventasPtr = 0;
    if(averiasQueue) averiasQueue.forEach(r=>r.done=false);
    if(mantenimientoQueue) mantenimientoQueue.forEach(r=>{ r.done=false; r.warned=false; });
    selected = null;
    hideSelection();
    buildFleet();
    seedInitialBlockages();
    if(bloqueosQueue) activateScheduledBlockages();
    if(averiasQueue) activateScheduledAverias();
    if(mantenimientoQueue) activateScheduledMantenimiento();
  }

  function seedInitialBlockages(){
    if(bloqueosQueue) return; // ya hay un horario de bloqueos cargado desde archivo
    // bloqueos ya planificados desde el inicio de la ejecución (archivo mensual)
    for(let i=0;i<2;i++){
      const chain = randomBlockageChain();
      addBlockageChain(chain, simMin + 200 + Math.random()*400);
    }
  }
