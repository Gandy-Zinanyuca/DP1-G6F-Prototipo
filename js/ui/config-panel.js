// Panel desplegable de configuración del semáforo (umbrales verde/ámbar).
// Depende de: core/state.js
"use strict";
  /* =================== CONFIG PANEL (semáforo thresholds) =================== */
  const cfgBtn = document.getElementById('cfgBtn');
  const cfgPanel = document.getElementById('cfgPanel');
  cfgBtn.addEventListener('click', e=>{ e.stopPropagation(); cfgPanel.classList.toggle('open'); });
  document.addEventListener('click', e=>{
    if(cfgPanel.classList.contains('open') && !cfgPanel.contains(e.target) && e.target!==cfgBtn && !cfgBtn.contains(e.target)){
      cfgPanel.classList.remove('open');
    }
  });
  const cfgGreen = document.getElementById('cfgGreen');
  const cfgAmber = document.getElementById('cfgAmber');
  const cfgGreenVal = document.getElementById('cfgGreenVal');
  const cfgAmberVal = document.getElementById('cfgAmberVal');
  function applyThresholdInputs(){
    let g = +cfgGreen.value, a = +cfgAmber.value;
    if(a >= g){ a = Math.max(5, g-5); cfgAmber.value = a; }
    thresholds.green = g; thresholds.amber = a;
    cfgGreenVal.textContent = g; cfgAmberVal.textContent = a;
  }
  cfgGreen.addEventListener('input', applyThresholdInputs);
  cfgAmber.addEventListener('input', applyThresholdInputs);

