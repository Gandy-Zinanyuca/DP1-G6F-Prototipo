// Bucle de animación (requestAnimationFrame) e inicialización de la app.
// Depende de: todos los anteriores — debe cargarse último
"use strict";
  /* =================== LOOP =================== */
  function frame(ts){
    if(lastTs==null) lastTs = ts;
    const dtRealSec = Math.min(0.25, (ts-lastTs)/1000);
    lastTs = ts;
    if(running){
      const dtMin = dtRealSec * SIM_MIN_PER_SEC;
      simStep(dtMin);
    }
    render();
    requestAnimationFrame(frame);
  }

  let domTimer = null;
  function startDomTimer(){
    if(domTimer) clearInterval(domTimer);
    domTimer = setInterval(updateDom, 350);
  }

  /* =================== INIT =================== */
  function init(){
    buildFleet();
    seedInitialBlockages();
    resizeCanvas();
    addLog('Simulación lista — presiona Iniciar para configurar la fecha, hora y escenario de arranque.', 'accent');
    startDomTimer();
    requestAnimationFrame(frame);
  }
  init();
