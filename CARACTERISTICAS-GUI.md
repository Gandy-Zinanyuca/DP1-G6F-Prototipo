# Características principales del GUI de PaqRap en Ruta

## 1. Descripción general

La interfaz de **PaqRap en Ruta** funciona como un centro de operaciones para configurar, ejecutar y monitorear entregas logísticas. El GUI está organizado alrededor de un mapa interactivo y ofrece módulos para registrar pedidos, administrar incidencias y consultar indicadores de desempeño.

La aplicación contempla tres escenarios:

- **Operación diaria:** espera el registro del primer pedido antes de iniciar.
- **Simulación de 5 días:** admite la carga de pedidos y bloqueos desde archivos.
- **Simulación hasta el colapso:** se ejecuta hasta que un pedido incumple su plazo.

## 2. Estructura de la interfaz

### Cabecera de operación

La cabecera permanece visible y concentra los controles y datos principales:

- Identidad de **PaqRap** y denominación del centro de operaciones.
- Selector de módulo.
- Escenario activo.
- Botón para iniciar o detener la ejecución.
- Duración real de la ejecución.
- Acción para reiniciar la simulación.
- Fecha, hora y día de simulación.
- Turno actual y aviso de refrigerio.
- Acceso a los datos de entrada.
- Configuración del semáforo de estados.
- Búsqueda de unidades.
- Acceso al panel de indicadores.

### Área principal

La vista predeterminada contiene:

- Mapa cartesiano de la operación.
- Controles de zoom y restablecimiento de vista.
- Leyenda persistente.
- Panel de búsqueda y filtros.
- Panel de detalle de la entidad seleccionada.
- Panel flotante de indicadores y eventos.

## 3. Módulos disponibles

### Mapa y simulación

Representa una retícula urbana de **70 km por 50 km**, con nodos separados por 1 km. Permite visualizar:

- Almacenes y sus niveles de inventario.
- Vehículos activos.
- Pedidos pendientes o asignados.
- Rutas calculadas.
- Recorrido reciente de las unidades.
- Bloqueos de calles.
- Vehículos averiados.
- Cumplimiento de pedidos mediante estados semánticos.

El usuario puede acercar, alejar y restablecer el mapa, desplazarse sobre él y seleccionar almacenes, vehículos o sectores para consultar información detallada.

### Registro de pedidos

Permite registrar pedidos manualmente mediante los siguientes datos:

- Cliente.
- Cantidad de paquetes.
- Modalidad o plazo de entrega.
- Coordenadas X e Y del destino.

También permite:

- Cargar varios pedidos mediante texto estructurado.
- Confirmar los datos antes del registro.
- Buscar pedidos por cliente.
- Filtrar por estado.
- Filtrar por modalidad.
- Consultar resultados en una tabla.

Las modalidades disponibles corresponden a entregas regulares de 36 horas y entregas priorizadas de 18, 12, 8 o 4 horas.

### Incidencias

Permite registrar manualmente:

- Averías de vehículos de tipo leve, moderado o grave.
- Bloqueos formados por cadenas de nodos.
- Duración estimada de los bloqueos.

El historial muestra el tipo de incidencia, su detalle y las horas de inicio y finalización.

## 4. Panel de indicadores

El panel flotante presenta información resumida de la operación:

- Pedidos activos y pendientes de asignación.
- Cumplimiento del SLA.
- Entregas realizadas durante el día.
- Incidencias activas.
- Cumplimiento por prioridad.
- Stock de los almacenes.
- Registro cronológico de eventos.

Los indicadores utilizan barras, porcentajes, valores numéricos y colores semánticos para facilitar la supervisión.

## 5. Configuración inicial

Antes de ejecutar un escenario se muestra un modal que permite configurar:

- Tipo de escenario.
- Fecha y hora iniciales, cuando corresponda.
- Cantidad de autos, motos y bicicletas.
- Capacidad de los almacenes intermedios.
- Horarios de inicio de los tres turnos.

El almacén central se considera de stock ilimitado.

## 6. Simbología logística

### Vehículos

| Tipo | Capacidad | Velocidad | Color base |
| --- | ---: | ---: | --- |
| Auto | 24 paquetes | 40 km/h | Azul |
| Moto | 8 paquetes | 25 km/h | Naranja |
| Bicicleta | 4 paquetes | 12 km/h | Verde |

Cada vehículo puede mostrar su carga, ruta, posición, pedido asignado, prioridad, tiempo estimado de llegada y estado operativo.

### Almacenes

Los almacenes se representan con marcadores sobre el mapa:

- **Almacén Central:** nodo `(25,15)` y stock ilimitado.
- **Almacén Nor-Oeste:** nodo `(12,38)` y capacidad configurable.
- **Almacén Este:** nodo `(55,27)` y capacidad configurable.

Los almacenes intermedios incluyen una barra de inventario coloreada según su nivel de stock.

### Rutas, pedidos e incidencias

- Las rutas asignadas se muestran con líneas discontinuas.
- El recorrido reciente del vehículo se representa mediante una estela atenuada.
- Los pedidos se dibujan como círculos cuyo tamaño depende de la cantidad.
- Los pedidos priorizados incluyen una marca adicional.
- Los bloqueos se muestran como segmentos rojos gruesos con extremos visibles.
- Los vehículos averiados presentan un contorno crítico.

## 7. Sistema visual

### Paleta principal del modo claro

| Función | Color |
| --- | --- |
| Fondo general | `#EEF2F0` |
| Superficies | `#FFFFFF` |
| Texto principal | `#12211D` |
| Texto secundario | `#3F544C` |
| Acento | `#0E7C86` |
| Estado correcto | `#2E9E5B` |
| Advertencia | `#B9821B` |
| Estado crítico | `#C94040` |

El proyecto también incluye tokens específicos para modo oscuro y puede activarlo según la preferencia de color del sistema.

### Tipografía

La interfaz combina tres familias:

- **Sora:** marca, títulos y controles destacados.
- **Manrope:** texto general, etiquetas y formularios.
- **IBM Plex Mono:** reloj, códigos, cantidades y datos tabulares.

### Componentes visuales

El GUI utiliza:

- Botones primarios, secundarios, de ejecución e iconográficos.
- Campos de texto, campos numéricos, selectores, controles de hora y archivos.
- Tarjetas de indicadores.
- Barras de progreso e inventario.
- Tablas de pedidos e incidencias.
- Modales de configuración y confirmación.
- Paneles flotantes de búsqueda, configuración, detalle e indicadores.
- Mensajes de error y estados vacíos.

## 8. Estados y retroalimentación

La interfaz diferencia los siguientes estados principales:

- Simulación sin configurar, en ejecución o detenida.
- Operación diaria en espera del primer pedido.
- Pedido registrado, pendiente, asignado, entregado o incumplido.
- Vehículo disponible, en ruta, entregando, retornando, en refrigerio o averiado.
- Almacén en nivel correcto, en riesgo o crítico.
- Evento informativo, satisfactorio, de advertencia o crítico.

Las acciones importantes actualizan textos, iconos, colores, indicadores y el registro de eventos.

## 9. Adaptación y accesibilidad

El prototipo incluye algunas medidas iniciales de adaptación y accesibilidad:

- Cabecera con distribución flexible.
- Panel de indicadores adaptable en pantallas menores de 700 px.
- Nombres accesibles en varios botones iconográficos.
- Indicador de foco visible en los controles de zoom.
- Respeto de la preferencia `prefers-reduced-motion` en ciertas animaciones.
- Modos claro y oscuro.
- Etiquetas visibles en formularios.

Como mejoras recomendadas se identifican:

- Completar el foco visible y la navegación por teclado en todos los componentes.
- Proporcionar una alternativa textual al contenido del mapa dibujado en `canvas`.
- Aumentar los textos esenciales menores de 12 px.
- Evitar comunicar estados únicamente mediante color.
- Sustituir los emojis operativos por iconos SVG controlados.
- Diferenciar visualmente las acciones primarias de las acciones destructivas.
- Completar el comportamiento adaptable para teléfonos y tabletas.

## 10. Tecnologías de implementación

El GUI está desarrollado con:

- HTML para la estructura de la aplicación.
- CSS con variables para colores, radios, sombras y estados visuales.
- JavaScript sin framework para la simulación y actualización de la interfaz.
- Elemento `canvas` para la representación del mapa.
- Google Fonts para Sora, Manrope e IBM Plex Mono.

Los archivos principales relacionados con la interfaz son:

- `index.html`
- `css/styles.css`
- `js/ui/render.js`
- `js/ui/dom-update.js`
- `js/ui/run-controls.js`
- `js/ui/modules-nav.js`
- `js/ui/pedidos-module.js`
- `js/ui/incidencias-module.js`
- `js/ui/selection-panel.js`
- `js/ui/map-input.js`
- `js/ui/search-filter.js`
- `js/ui/config-panel.js`
- `js/ui/data-panel.js`
