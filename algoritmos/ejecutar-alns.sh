#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
[[ -f "$script_dir/out/pe/pucp/paqrap/EjecutarALNS.class" ]] || bash "$script_dir/compilar.sh"
java -cp "$script_dir/out" pe.pucp.paqrap.EjecutarALNS "$@"
