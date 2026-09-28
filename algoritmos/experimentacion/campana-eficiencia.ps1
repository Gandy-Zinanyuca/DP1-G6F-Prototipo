<#
Campana de eficiencia: ALNS vs TS sobre 5 meses x 4 semillas (40 corridas),
cada una hasta COLAPSO_PLANIFICACION o 4320 ciclos (~1 mes simulado).

Reanudable: si una carpeta de salida ya tiene un resumen.csv con datos, se omite.
Si existe pero esta vacia/incompleta (corrida anterior interrumpida), se borra y se
vuelve a lanzar. Cada proceso se espera con WaitForExit real (no se asume Ok por
temporizacion), y el progreso se escribe linea a linea a medida que cada corrida
termina, para que un corte a mitad de camino deje un registro fiel de que
termino y que no.
#>
param(
    [int]$BatchSize = 8,
    [string]$HeapSize = '2g'
)
$ErrorActionPreference = 'Stop'

$repoRoot = 'D:\2026-2\DP1\Prototipo\DP1-G6F-Prototipo'
Set-Location $repoRoot

$ventas = 'algoritmos/alns/data/ventas.v20260909'
$bloqueos = 'algoritmos/alns/data/bloqueos.v20260909'
$resultadosBase = 'algoritmos/experimentacion/resultados'
$logsDir = Join-Path $resultadosBase 'logs-eficiencia'
$progresoLog = Join-Path $logsDir 'orquestador.log'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

function Log([string]$msg) {
    $linea = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg"
    Write-Host $linea
    Add-Content -Path $progresoLog -Value $linea -Encoding utf8
}

function TieneResultado([string]$carpeta) {
    $resumen = Join-Path $carpeta 'resumen.csv'
    if (-not (Test-Path $resumen)) { return $false }
    $lineas = @(Get-Content $resumen -ErrorAction SilentlyContinue)
    return ($lineas.Count -ge 2)
}

$meses = @('2026-02', '2026-08', '2027-03', '2027-04', '2027-06')
$semillas = @(20262, 20263, 20264, 20265)
$algoritmos = @('ALNS', 'TS')

$jobs = @()
foreach ($mes in $meses) {
    $mesCompacto = $mes -replace '-', ''
    foreach ($algo in $algoritmos) {
        foreach ($semilla in $semillas) {
            $jobs += [pscustomobject]@{
                Algo        = $algo
                Mes         = $mes
                MesCompacto = $mesCompacto
                Semilla     = $semilla
                Carpeta     = "$resultadosBase/campana-eficiencia-$algo-$mesCompacto-$semilla"
            }
        }
    }
}

Log "=== Inicio de campana de eficiencia: $($jobs.Count) corridas objetivo ==="

$pendientes = @()
foreach ($j in $jobs) {
    if (TieneResultado $j.Carpeta) {
        Log "OMITIDO (ya tiene resultado valido): $($j.Algo) $($j.Mes) semilla=$($j.Semilla)"
        continue
    }
    if (Test-Path $j.Carpeta) {
        Log "Limpiando carpeta incompleta de corrida anterior: $($j.Carpeta)"
        Remove-Item -Recurse -Force $j.Carpeta
    }
    $pendientes += $j
}

Log "Pendientes por correr: $($pendientes.Count) de $($jobs.Count)"

$loteNum = 0
for ($i = 0; $i -lt $pendientes.Count; $i += $BatchSize) {
    $loteNum++
    $fin = [Math]::Min($i + $BatchSize - 1, $pendientes.Count - 1)
    $lote = $pendientes[$i..$fin]
    $etiquetas = ($lote | ForEach-Object { "$($_.Algo)-$($_.MesCompacto)-$($_.Semilla)" }) -join ', '
    Log "Lanzando lote ${loteNum} ($($lote.Count) procesos): $etiquetas"

    $procs = @()
    foreach ($j in $lote) {
        $outLog = Join-Path $logsDir "$($j.Algo)-$($j.MesCompacto)-$($j.Semilla).out.log"
        $errLog = Join-Path $logsDir "$($j.Algo)-$($j.MesCompacto)-$($j.Semilla).err.log"
        $argList = @(
            '-Dfile.encoding=UTF-8', "-Xmx$HeapSize",
            '-cp', 'algoritmos/out', 'pe.pucp.paqrap.SimulacionComparada',
            $j.Algo, $ventas, $bloqueos, '-', $j.Mes, $j.Carpeta, '300', "$($j.Semilla)", '4320'
        )
        $p = Start-Process -FilePath 'java' -ArgumentList $argList -WorkingDirectory $repoRoot `
            -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -NoNewWindow
        $procs += [pscustomobject]@{ Job = $j; Proc = $p; Inicio = (Get-Date) }
    }
    $pidsTxt = ($procs | ForEach-Object { $_.Proc.Id }) -join ','
    Log "PIDs lote ${loteNum} : $pidsTxt"

    foreach ($pr in $procs) {
        $pr.Proc.WaitForExit()
        $dur = (Get-Date) - $pr.Inicio
        $codigo = $pr.Proc.ExitCode
        $ok = TieneResultado $pr.Job.Carpeta
        $etiqueta = "$($pr.Job.Algo)-$($pr.Job.MesCompacto)-$($pr.Job.Semilla)"
        Log "Fin $etiqueta : exitCode=$codigo duracion=$($dur.ToString('hh\:mm\:ss')) resultadoValido=$ok"
    }
    Log "Lote ${loteNum} terminado ($($lote.Count) corridas procesadas)"
}

$totalOk = 0
$sinResultado = @()
foreach ($j in $jobs) {
    if (TieneResultado $j.Carpeta) { $totalOk++ }
    else { $sinResultado += "$($j.Algo)-$($j.MesCompacto)-$($j.Semilla)" }
}
Log "=== CAMPANA FINALIZADA: $totalOk/$($jobs.Count) corridas con resultado valido ==="
if ($sinResultado.Count -gt 0) {
    Log "SIN RESULTADO ($($sinResultado.Count)): $($sinResultado -join ', ')"
}
