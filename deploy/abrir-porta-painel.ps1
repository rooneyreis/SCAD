<#
.SYNOPSIS
    Abre na Firewall do Windows APENAS a porta do painel do SCAD (8080, TCP de entrada), para que o
    painel possa ser aberto a partir de um telemóvel/portátil na mesma rede: http://<IP deste PC>:8080

.DESCRIPTION
    Executar numa PowerShell aberta como Administrador. Idempotente (substitui a regra anterior).
    Se o WSL estiver em modo "mirrored" (%USERPROFILE%\.wslconfig), cria também a regra na
    firewall Hyper-V usada por esse modo.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1 -Remover
#>
param([switch] $Remover, [int] $Porta = 8080)

$ErrorActionPreference = 'Stop'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Error 'Execute este script numa janela PowerShell aberta como Administrador.' }

$nome = "SCAD painel $Porta"
Get-NetFirewallRule -DisplayName $nome -ErrorAction SilentlyContinue | Remove-NetFirewallRule
$wslVm = '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}'   # identificador da VM do WSL na firewall Hyper-V
if (Get-Command Get-NetFirewallHyperVRule -ErrorAction SilentlyContinue) {
    Get-NetFirewallHyperVRule -Name "SCAD-painel-$Porta" -ErrorAction SilentlyContinue | Remove-NetFirewallHyperVRule
}
if ($Remover) { Write-Host "Regra '$nome' removida."; return }

New-NetFirewallRule -DisplayName $nome -Direction Inbound -Protocol TCP -LocalPort $Porta -Action Allow -Profile Any | Out-Null
Write-Host "Firewall do Windows: porta $Porta aberta (entrada TCP, todos os perfis)."

$wslconfig = Join-Path $env:USERPROFILE '.wslconfig'
if ((Test-Path $wslconfig) -and (Select-String -Path $wslconfig -Pattern '^\s*networkingMode\s*=\s*mirrored' -Quiet)) {
    if (Get-Command New-NetFirewallHyperVRule -ErrorAction SilentlyContinue) {
        New-NetFirewallHyperVRule -Name "SCAD-painel-$Porta" -DisplayName $nome -Direction Inbound `
            -VMCreatorId $wslVm -Protocol TCP -LocalPorts $Porta -Action Allow | Out-Null
        Write-Host "WSL em modo mirrored: regra também criada na firewall Hyper-V."
    }
    Write-Warning ("O WSL está em modo 'mirrored'. Se o painel não abrir pelo IP do PC, comente a linha " +
        "networkingMode=mirrored em $wslconfig, execute 'wsl --shutdown' e reinicie o Docker Desktop.")
}

$ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|Loopback' }
foreach ($ip in $ips) { Write-Host ("Painel: http://{0}:{1}   ({2})" -f $ip.IPAddress, $Porta, $ip.InterfaceAlias) }
