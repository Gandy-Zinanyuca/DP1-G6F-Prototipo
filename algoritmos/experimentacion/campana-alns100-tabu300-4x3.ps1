<#
Campana ALNS100 vs TS300: 4 fechas x 3 semillas x 2 algoritmos.

Cada fecha empieza con flota, almacenes y operaciones reiniciados. Las corridas
son secuenciales y el orden TS/ALNS se alterna por bloque fecha-semilla para no
favorecer sistematicamente a un motor por calentamiento de la maquina.

Configuracion piloto fijada antes de la campana: TS=300 iteraciones; ALNS=100,
destruccion=2; presupuesto temporal=0 porque Ta es una variable de respuesta.
#>
param(
    [string[]]$Meses = @('2026-02', '2026-08', '2027-03', '2027-06'),
    [long[]]$Semillas = @(20262, 20263, 20264),
    [int]$IteracionesTS = 300,
    [int]$IteracionesALNS = 100,
    [int]$DestruccionALNS = 2,
    [int]$MaxCiclos = 4320,
    [int]$Sa = 10,
    [double]$FactorCarga = 1,
    [string]$HeapSize = '2g',
    [string]$Salida = 'algoritmos/experimentacion/resultados/campana-alns100-tabu300-4x3',
    [switch]$NoCompilar,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repoRoot
$ventas = 'algoritmos/alns/data/ventas.v20260909'
$bloqueos = 'algoritmos/alns/data/bloqueos.v20260909'

function TieneResultado([string]$carpeta) {
    $resumen = Join-Path $carpeta 'resumen.csv'
    if (-not (Test-Path $resumen)) { return $false }
    return (@(Get-Content $resumen -ErrorAction SilentlyContinue).Count -ge 2)
}

function NuevaRutaPreservandoAnterior([string]$carpeta) {
    if (-not (Test-Path $carpeta)) { return $carpeta }
    if (TieneResultado $carpeta) { return $carpeta }
    $sufijo = Get-Date -Format 'yyyyMMdd-HHmmss'
    $preservada = "$carpeta-incompleta-$sufijo"
    Move-Item -LiteralPath $carpeta -Destination $preservada
    Write-Host "Corrida incompleta preservada en $preservada"
    return $carpeta
}

$trabajos = @()
for ($m = 0; $m -lt $Meses.Count; $m++) {
    $mes = $Meses[$m]
    $mesCompacto = $mes -replace '-', ''
    for ($s = 0; $s -lt $Semillas.Count; $s++) {
        $orden = if ((($m + $s) % 2) -eq 0) { @('TS', 'ALNS') } else { @('ALNS', 'TS') }
        for ($o = 0; $o -lt $orden.Count; $o++) {
            $algoritmo = $orden[$o]
            $trabajos += [pscustomobject]@{
                Fecha = $mes
                Semilla = $Semillas[$s]
                Algoritmo = $algoritmo
                OrdenEnBloque = $o + 1
                Carpeta = Join-Path $Salida "corridas/$mesCompacto/$($Semillas[$s])/$algoritmo"
            }
        }
    }
}

Write-Host "Campana: $($trabajos.Count) corridas secuenciales; salida=$Salida"
if ($DryRun) {
    $trabajos | Format-Table Fecha, Semilla, OrdenEnBloque, Algoritmo, Carpeta -AutoSize
    return
}

if (-not $NoCompilar) {
    & .\algoritmos\compilar.bat -Pruebas
    if ($LASTEXITCODE -ne 0) { throw "La compilacion/pruebas fallo con codigo $LASTEXITCODE" }
}

$logsDir = Join-Path $Salida 'logs'
$manifestPath = Join-Path $Salida 'corridas.csv'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

$numero = 0
foreach ($j in $trabajos) {
    $numero++
    if (TieneResultado $j.Carpeta) {
        Write-Host "[$numero/$($trabajos.Count)] OMITIDA $($j.Fecha) semilla=$($j.Semilla) $($j.Algoritmo)"
        continue
    }
    $carpeta = NuevaRutaPreservandoAnterior $j.Carpeta
    $etiqueta = "$($j.Fecha -replace '-', '')-$($j.Semilla)-$($j.Algoritmo)"
    $outLog = Join-Path $logsDir "$etiqueta.out.log"
    $errLog = Join-Path $logsDir "$etiqueta.err.log"
    $factorTexto = [string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0}', $FactorCarga)
    $argumentos = @(
        '-Dfile.encoding=UTF-8', "-Xmx$HeapSize", '-cp', 'algoritmos/out',
        'pe.pucp.paqrap.SimulacionComparada', $j.Algoritmo, $ventas, $bloqueos, '-',
        $j.Fecha, $carpeta, "$IteracionesTS", "$($j.Semilla)", "$MaxCiclos", "$Sa", $factorTexto,
        "$IteracionesALNS", '0', "$DestruccionALNS"
    )
    $inicio = Get-Date
    Write-Host "[$numero/$($trabajos.Count)] INICIO $etiqueta orden=$($j.OrdenEnBloque)"
    $proceso = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -NoNewWindow
    $proceso.WaitForExit()
    $fin = Get-Date
    $valido = TieneResultado $carpeta
    $registro = [pscustomobject]@{
        fecha = $j.Fecha
        semilla = $j.Semilla
        algoritmo = $j.Algoritmo
        orden_en_bloque = $j.OrdenEnBloque
        inicio_real = $inicio.ToString('o')
        fin_real = $fin.ToString('o')
        tiempo_proceso_real_ms = [Math]::Round(($fin - $inicio).TotalMilliseconds, 3)
        exit_code = $proceso.ExitCode
        resultado_valido = $valido
        iteraciones_ts = $IteracionesTS
        iteraciones_alns = $IteracionesALNS
        destruccion_alns = $DestruccionALNS
        presupuesto_ms = 0
        max_ciclos = $MaxCiclos
        sa_min = $Sa
        factor_carga = $FactorCarga
        carpeta = $carpeta
    }
    $registro | Export-Csv -Path $manifestPath -NoTypeInformation -Encoding utf8 -Append
    Write-Host "[$numero/$($trabajos.Count)] FIN $etiqueta exit=$($proceso.ExitCode) valido=$valido tiempo=$([Math]::Round(($fin-$inicio).TotalMinutes, 2)) min"
    if ($proceso.ExitCode -ne 0 -or -not $valido) {
        throw "Fallo la corrida $etiqueta. Revise $errLog y $outLog"
    }
}

$completas = @($trabajos | Where-Object { TieneResultado $_.Carpeta }).Count
Write-Host "Campana terminada: $completas/$($trabajos.Count) corridas completas."
if ($completas -ne $trabajos.Count) { exit 2 }
