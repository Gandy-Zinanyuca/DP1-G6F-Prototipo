// Mecanismo genérico de carga de archivo con vista previa + modal de confirmación, reutilizado por Pedidos, Bloqueos, Averías y Mantenimiento.
// Depende de: data/file-io.js, core/log.js
"use strict";
  /* =================== ARCHIVOS DE ENTRADA: viven dentro de cada módulo y piden confirmación antes de aplicarse =================== */
  // Igual que el alta manual y la carga masiva de pedidos: primero se parsea y se muestra un
  // adelanto en un modal, y solo al confirmar se aplica el archivo a la simulación.
  let filePendiente = null;
  const fileConfirmModal = document.getElementById('fileConfirmModal');
  const fcTitle = document.getElementById('fcTitle');
  const fcCantidad = document.getElementById('fcCantidad');
  const fcPreview = document.getElementById('fcPreview');

  function wireFileInput(inputId, statusId, parseFn, previewFn, commitFn, summaryFn, titleText, onLoaded){
    const input = document.getElementById(inputId);
    const status = document.getElementById(statusId);
    input.addEventListener('change', ()=>{
      const file = input.files[0];
      if(!file) return;
      const reader = new FileReader();
      reader.onload = ()=>{
        const parsed = parseFn(reader.result);
        if(!parsed.length){
          status.textContent = 'No se reconoció ningún registro válido en ese formato.';
          status.className = 'data-status critical';
          input.value = '';
          return;
        }
        filePendiente = { parsed, commitFn, summaryFn, status, onLoaded, input };
        fcTitle.textContent = titleText;
        fcCantidad.textContent = parsed.length;
        fcPreview.innerHTML = previewFn(parsed);
        fileConfirmModal.classList.add('open');
      };
      reader.readAsText(file);
    });
  }

  document.getElementById('btnFileConfirmCancel').addEventListener('click', ()=>{
    if(filePendiente) filePendiente.input.value = '';
    filePendiente = null;
    fileConfirmModal.classList.remove('open');
  });

  document.getElementById('btnFileConfirmOk').addEventListener('click', ()=>{
    if(!filePendiente) return;
    const { parsed, commitFn, summaryFn, status, onLoaded, input } = filePendiente;
    const res = commitFn(parsed);
    status.textContent = summaryFn(res);
    status.className = 'data-status good';
    input.value = '';
    filePendiente = null;
    fileConfirmModal.classList.remove('open');
    if(onLoaded) onLoaded();
  });

  wireFileInput('fileVentas', 'statusVentas', parseVentas, previewVentas, commitVentasFile,
    res => `Cargado: ${res.count} registros (${res.historicos} históricos, ${res.count-res.historicos} en cola).`,
    'Confirmar carga de pedidos', ()=> renderPedidosModule());

  wireFileInput('fileBloqueos', 'statusBloqueos', parseBloqueos, previewBloqueos, commitBloqueosFile,
    res => `Cargado: ${res.count} registros (${res.vigentes} vigentes desde ya).`,
    'Confirmar carga de bloqueos', ()=> renderIncidenciasModule());

  wireFileInput('fileAverias', 'statusAverias', parseAveriasFile, previewAverias, commitAveriasFile,
    res => `Cargado: ${res.count} registros (${res.inmediatas} aplicadas de inmediato).`,
    'Confirmar carga de averías', ()=> renderAveriasModule());

  wireFileInput('fileMantenimiento', 'statusMantenimiento', parseMantenimientoFile, previewMantenimiento, commitMantenimientoFile,
    res => `Cargado: ${res.count} registros del plan (${res.inmediatos} aplicados de inmediato).`,
    'Confirmar plan de mantenimiento preventivo', ()=> renderMantenimientoModule());
