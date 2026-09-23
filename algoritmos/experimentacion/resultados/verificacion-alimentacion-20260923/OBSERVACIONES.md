# Verificacion de alimentacion

Esta corrida usa las dos reglas nuevas sobre la base Git 6599997: banda de INICIO de alimentacion hasta una hora antes del relevo, y reconocimiento de 60 minutos continuos de inactividad. El encabezado de metadatos conserva la etiqueta anterior porque el texto identificador se actualizo despues de iniciar la JVM; el comportamiento ejecutado ya incluye ambas reglas.

Datos desde enero de 2026, factor 1, Sa 10 minutos, semilla 1, dos iteraciones por llamada y limite de 2880 ciclos (20 dias). Prueba funcional, no campaña estadistica final. TS se ejecuto primero y ALNS despues, con restauracion de condiciones iniciales.

| Motor | Fin | Pedidos entregados | Paquetes entregados | Holgura media/minima real (min) |
|---|---|---:|---:|---|
| TS | Limite, 21/01/2026 00:00 | 364 | 1940 | 940.04 / 0.50 |
| ALNS | Limite, 21/01/2026 00:00 | 364 | 1937 | 944.56 / 1.50 |

Ambos superan el ciclo del 20/01 a las 12:10 donde la campaña TS anterior colapsaba. Tambien superan la fecha 12/01 a las 19:40 registrada en la primera corrida historica disponible de la campaña ALNS actual. No se afirma que la mejora provenga exclusivamente de una de las reglas: cambiaron ambas y estas corridas usan 2 iteraciones frente a las 300 de aquellas campañas. Es necesario repetir las mismas semillas y presupuesto de busqueda con las nuevas reglas para establecer una comparacion experimental.

La diferencia en paquetes no implica pedidos duplicados: los contadores incluyen partes entregadas de pedidos aun incompletos y las trayectorias difieren. Las holguras reales incluyen solo pedidos completados. El limite de ciclos no demuestra fin de datos ni ausencia de colapso posterior. Las mediciones de Ta no deben compararse con las campañas de 300 iteraciones; durante parte de esta verificacion tambien se ejecutaron pruebas automaticas.

Validacion: suite comun, experimentacion, simulacion y AlimentacionTest aprobadas. Esta ultima comprueba ventanas de los tres turnos, pausa de 59 minutos, disponibilidad tras retorno, reinicio entre turnos, pausas futuras y entrega de los 7 paquetes del pedido de la linea 352 en una instancia aislada para ambos motores.
