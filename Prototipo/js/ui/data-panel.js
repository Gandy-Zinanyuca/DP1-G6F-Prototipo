// Panel desplegable de carga de archivos de ventas y bloqueos.
// Depende de: data/file-io.js
"use strict";
  /* =================== PANEL DE DATOS DE ENTRADA (archivos) =================== */
  const dataBtn = document.getElementById('dataBtn');
  const dataPanel = document.getElementById('dataPanel');
  dataBtn.addEventListener('click', e=>{ e.stopPropagation(); dataPanel.classList.toggle('open'); });
  document.addEventListener('click', e=>{
    if(dataPanel.classList.contains('open') && !dataPanel.contains(e.target) && e.target!==dataBtn && !dataBtn.contains(e.target)){
      dataPanel.classList.remove('open');
    }
  });
  function wireFileInput(inputId, statusId, loaderFn){
    const input = document.getElementById(inputId);
    const status = document.getElementById(statusId);
    input.addEventListener('change', ()=>{
      const file = input.files[0];
      if(!file) return;
      const reader = new FileReader();
      reader.onload = ()=>{
        const res = loaderFn(reader.result);
        status.textContent = res.ok ? `Cargado: ${res.count} registros.` : 'No se reconoció ningún registro válido en ese formato.';
        status.className = 'data-status ' + (res.ok ? 'good' : 'critical');
      };
      reader.readAsText(file);
    });
  }
  wireFileInput('fileVentas', 'statusVentas', loadVentasFile);
  wireFileInput('fileBloqueos', 'statusBloqueos', loadBloqueosFile);

