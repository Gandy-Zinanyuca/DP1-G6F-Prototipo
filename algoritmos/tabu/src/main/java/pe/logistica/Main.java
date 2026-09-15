package pe.logistica;

import pe.logistica.data.*;
import pe.logistica.model.*;
import pe.logistica.tabu.TabuSearchPlanner;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;

/** CLI sin frameworks. Sin argumentos ejecuta los datos demo incluidos en el JAR. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        int codigo = ejecutar(args, System.out, System.err);
        if (codigo != 0) System.exit(codigo);
    }
    public static int ejecutar(String[] args, PrintStream out, PrintStream err) {
        try {
            Map<String, String> opciones = cargarConfiguracion(opciones(args));
            if (opciones.containsKey("ayuda")) { ayuda(out); return 0; }
            if (opciones.containsKey("datos") && (opciones.containsKey("pedidos") || opciones.containsKey("bloqueos")
                    || opciones.containsKey("mantenimientos")))
                throw new IllegalArgumentException("--datos ya contiene ventas, bloqueos y mantenimiento; use archivos individuales sin --datos");
            boolean demo = !opciones.containsKey("pedidos") && !opciones.containsKey("datos");
            boolean publicado = !demo || opciones.containsKey("config");
            int anio = Integer.parseInt(opciones.getOrDefault("anio", "2026"));
            int mes = Integer.parseInt(opciones.getOrDefault("mes", "9"));
            YearMonth.of(anio, mes);
            LocalDateTime t = LocalDateTime.parse(opciones.getOrDefault("tiempo", publicado ? "2026-09-09T00:00" : "2026-09-11T13:30"));
            var config = new ConfiguracionTabu(Long.parseLong(opciones.getOrDefault("sa", "30")),
                    Integer.parseInt(opciones.getOrDefault("k", "4")), Integer.parseInt(opciones.getOrDefault("iteraciones", "100")),
                    Integer.parseInt(opciones.getOrDefault("tenencia", "7")));
            var pp = new PedidoParser(); var bp = new BloqueoParser(); var mp = new MantenimientoParser();
            List<Pedido> pedidos; List<Bloqueo> bloqueos; List<Mantenimiento> mantenimientos;
            if (opciones.containsKey("datos")) {
                var datos = new DatasetLoader().cargar(Path.of(opciones.get("datos")), t, config.scMinutos());
                pedidos = datos.pedidos(); bloqueos = datos.bloqueos(); mantenimientos = datos.mantenimientos();
            } else {
                pedidos = demo ? recurso("pedidos.txt", s -> pp.parsear(s, anio, mes))
                        : pp.leer(Path.of(opciones.get("pedidos")), anio, mes);
                bloqueos = opciones.containsKey("bloqueos") ? bp.leer(Path.of(opciones.get("bloqueos")), anio, mes)
                        : demo ? recurso("bloqueos.txt", s -> bp.parsear(s, anio, mes)) : List.of();
                mantenimientos = opciones.containsKey("mantenimientos") ? mp.leer(Path.of(opciones.get("mantenimientos")))
                        : demo ? recurso("mantenimientos.txt", mp::parsear) : List.of();
            }
            Nodo central = nodo(opciones.getOrDefault("central", publicado ? "27,14" : "0,0"));
            Nodo noroeste = nodo(opciones.getOrDefault("noroeste", "12,38"));
            Nodo este = nodo(opciones.getOrDefault("este", "57,27"));
            List<Vehiculo> vehiculos = opciones.containsKey("vehiculos")
                    ? Arrays.stream(opciones.get("vehiculos").split(",", -1)).map(String::trim).map(c -> new Vehiculo(c, central)).toList()
                    : publicado ? ReferenciaProyecto.flota(central)
                    : Arrays.stream("TA01,TA02,TM01,TM02,TB01".split(",")).map(c -> new Vehiculo(c, central)).toList();
            ParametrosOperacion base = publicado ? ParametrosOperacion.publicados() : ParametrosOperacion.legado();
            var velocidades = new EnumMap<TipoVehiculo, Double>(TipoVehiculo.class);
            for (TipoVehiculo tipo : TipoVehiculo.values()) velocidades.put(tipo, Double.parseDouble(opciones.getOrDefault(
                    "velocidad-" + tipo.name().toLowerCase(Locale.ROOT), Double.toString(base.velocidad(tipo)))));
            String plazo = opciones.getOrDefault("plazo", base.plazoIncluyeServicio() ? "FIN_SERVICIO" : "LLEGADA");
            if (!Set.of("LLEGADA", "FIN_SERVICIO").contains(plazo)) throw new IllegalArgumentException("--plazo: LLEGADA o FIN_SERVICIO");
            String nodos = opciones.getOrDefault("bloqueo-nodos", Boolean.toString(base.bloquearNodos()));
            if (!Set.of("true", "false").contains(nodos)) throw new IllegalArgumentException("--bloqueo-nodos: true o false");
            var parametros = new ParametrosOperacion(velocidades, Integer.parseInt(opciones.getOrDefault("servicio", "60")),
                    plazo.equals("FIN_SERVICIO"), Boolean.parseBoolean(nodos));
            var estado = new EstadoOperacion(t, pedidos, vehiculos, bloqueos, mantenimientos, parametros);
            var resultado = new TabuSearchPlanner().ejecutar(estado, config);
            out.println("Perfil: " + (publicado ? "referencias publicadas 2026-09-09" : "demo sintetica original"));
            out.println("Central: " + central + "; referencias sin recarga en MVP: NO=" + noroeste + ", E=" + este);
            out.println("Datos cargados: " + pedidos.size() + " pedidos, " + bloqueos.size() + " bloqueos, " + mantenimientos.size() + " mantenimientos");
            ConsoleReport.imprimir(resultado, out, opciones.containsKey("detalle-caminos"));
            return resultado.metricas().factibilidadGlobal() ? 0 : 1;
        } catch (IOException | IllegalArgumentException | DateTimeException | ArithmeticException e) {
            err.println("Error: " + e.getMessage()); err.println("Use --ayuda para ver los parametros."); return 2;
        }
    }
    private static Nodo nodo(String texto) {
        String[] xy = texto.split(",", -1);
        if (xy.length != 2) throw new IllegalArgumentException("Coordenada esperada: x,y");
        return new Nodo(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
    }
    private static final Set<String> CON_VALOR = Set.of("pedidos", "bloqueos", "mantenimientos", "vehiculos", "anio", "mes",
            "tiempo", "sa", "k", "iteraciones", "tenencia", "config", "datos", "central", "noroeste", "este",
            "velocidad-ta", "velocidad-tm", "velocidad-tb", "servicio", "plazo", "bloqueo-nodos");
    private static Map<String, String> cargarConfiguracion(Map<String, String> cli) throws IOException {
        if (!cli.containsKey("config")) return cli;
        Path archivo = Path.of(cli.get("config")).toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) { properties.load(reader); }
        Map<String, String> combinado = new LinkedHashMap<>();
        for (String clave : properties.stringPropertyNames()) {
            if (!CON_VALOR.contains(clave) || clave.equals("config")) throw new IllegalArgumentException("Parametro de configuracion desconocido: " + clave);
            String valor = properties.getProperty(clave).trim();
            if (Set.of("datos", "pedidos", "bloqueos", "mantenimientos").contains(clave))
                valor = archivo.getParent().resolve(valor).normalize().toString();
            combinado.put(clave, valor);
        }
        combinado.putAll(cli); return combinado;
    }
    private static Map<String, String> opciones(String[] args) {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Argumento inesperado: " + args[i]);
            String clave = args[i].substring(2);
            if (resultado.containsKey(clave)) throw new IllegalArgumentException("Opcion repetida: " + clave);
            if (clave.equals("ayuda") || clave.equals("detalle-caminos")) { resultado.put(clave, "true"); continue; }
            if (!CON_VALOR.contains(clave)) throw new IllegalArgumentException("Opcion desconocida: " + clave);
            if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Falta valor para " + clave);
            resultado.put(clave, args[i]);
        }
        return resultado;
    }
    private static <T> List<T> recurso(String nombre, Function<String, T> parser) throws IOException {
        InputStream entrada = Main.class.getResourceAsStream("/demo/" + nombre);
        if (entrada == null) throw new IOException("Recurso demo no encontrado: " + nombre);
        try (var lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            return lector.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).map(parser).toList();
        }
    }
    private static void ayuda(PrintStream out) {
        out.println("""
                Planificador logistico - Java 17+ - Tabu Search
                java -jar target/planificador-tabu-1.0.0.jar [opciones]
                Sin argumentos: dataset demo, T=2026-09-11T13:30.

                --config ARCHIVO           Propiedades UTF-8; rutas relativas a su carpeta. CLI tiene prioridad.
                --datos CARPETA            Dataset publicado (ventas/, bloqueos/, mant.preventivo.09.10.txt).
                --pedidos ARCHIVO           Pedidos mensuales; sin --datos ni --pedidos activa demo.
                --bloqueos ARCHIVO          Bloqueos del mes (opcional con pedidos propios).
                --mantenimientos ARCHIVO    Calendario preventivo (opcional).
                --vehiculos TA01,TM01,TB01  Flota explicita; dataset real: 10 TA, 15 TM y 12 TB por defecto.
                --central 27,14            Origen y retorno. Demo sintetica: 0,0.
                --noroeste 12,38 --este 57,27  Almacenes de referencia, sin recargas en este MVP.
                --velocidad-ta 20 --velocidad-tm 40 --velocidad-tb 14  Km/h por tipo.
                --servicio 60              Minutos por entrega.
                --plazo LLEGADA            LLEGADA o FIN_SERVICIO. Publicado: LLEGADA.
                --bloqueo-nodos true        Cierre de calles incidentes a los nodos bloqueados.
                --anio 2026 --mes 9         Anio y mes de los archivos mensuales.
                --tiempo 2026-09-11T13:30    Instante simulado T.
                --sa 30 --k 4              Sc = Sa * K, en minutos.
                --iteraciones 100          Maximo de iteraciones (0: solo inicial).
                --tenencia 7               Iteraciones durante las que se prohibe la reversa.
                --detalle-caminos          Imprimir calles unitarias y sus horas de cruce.
                --ayuda                    Mostrar esta ayuda.

                Codigos de salida: 0 factible/ayuda, 1 inicial no factible, 2 error de entrada.
                """);
    }
}
