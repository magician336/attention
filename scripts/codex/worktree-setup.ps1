$ErrorActionPreference = "Stop"

function Write-SetupLog {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "s"), $Message
    $line | Tee-Object -FilePath $script:LogFile -Append
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$File,
        [Parameter(Mandatory = $false)][string[]]$Arguments = @()
    )

    Write-SetupLog ("Running: {0} {1}" -f $File, ($Arguments -join " "))
    & $File @Arguments 2>&1 | Tee-Object -FilePath $script:LogFile -Append
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $File"
    }
}

try {
    $root = $env:CODEX_WORKTREE_PATH
    if ([string]::IsNullOrWhiteSpace($root)) {
        $root = (& git rev-parse --show-toplevel).Trim()
    }
    if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "Cannot resolve the worktree root. Set CODEX_WORKTREE_PATH or run inside a Git worktree."
    }

    Set-Location -LiteralPath $root
    $script:LogFile = Join-Path $root ".codex-worktree-setup.log"
    Write-SetupLog "Starting Attention worktree setup in $root"

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "git is required but was not found on PATH."
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java is required but was not found on PATH."
    }

    if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME) -and [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $localSdk = "D:\Android\Sdk"
        if (Test-Path -LiteralPath $localSdk -PathType Container) {
            $env:ANDROID_HOME = $localSdk
            $env:ANDROID_SDK_ROOT = $localSdk
            Write-SetupLog "Using Android SDK at $localSdk"
        } else {
            Write-SetupLog "WARNING: ANDROID_HOME/ANDROID_SDK_ROOT is not set; Android tasks may need SDK configuration."
        }
    }

    $codegraph = Get-Command codegraph -ErrorAction SilentlyContinue
    if ($null -eq $codegraph) {
        if ($env:CODEGRAPH_REQUIRED -eq "1") {
            throw "CODEGRAPH_REQUIRED=1 but codegraph was not found on PATH."
        }
        Write-SetupLog "WARNING: codegraph was not found; skipping index setup."
    } else {
        $codegraphDir = Join-Path $root ".codegraph"
        $hasIndex = (Test-Path -LiteralPath $codegraphDir -PathType Container) -and ((@(Get-ChildItem -LiteralPath $codegraphDir -Force | Where-Object { $_.Name -ne ".gitignore" })).Count -gt 0)
        if ($hasIndex) {
            Invoke-LoggedCommand $codegraph.Source @("sync", $root)
        } else {
            Invoke-LoggedCommand $codegraph.Source @("init", $root)
        }
    }

    $gradle = Join-Path $root "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw "gradlew.bat was not found at the worktree root."
    }
    Invoke-LoggedCommand $gradle @("--no-daemon", ":domain:test")

    Write-SetupLog "Attention worktree setup completed successfully."
    exit 0
} catch {
    if ($script:LogFile) {
        Write-SetupLog ("SETUP FAILED: " + $_.Exception.Message)
    } else {
        Write-Error ("SETUP FAILED: " + $_.Exception.Message)
    }
    exit 1
}
