[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Split-Path -Parent $scriptDirectory
$mysqlImage = if ($env:ERP_LOCAL_TEST_MYSQL_IMAGE) { $env:ERP_LOCAL_TEST_MYSQL_IMAGE } else { "mysql:8.4" }
$mysqlContainer = "erp-local-test-mysql-$PID"
$mysqlDatabase = if ($env:ERP_LOCAL_TEST_MYSQL_DATABASE) { $env:ERP_LOCAL_TEST_MYSQL_DATABASE } else { "erp_codex_test" }
$mysqlUsername = if ($env:ERP_LOCAL_TEST_MYSQL_USERNAME) { $env:ERP_LOCAL_TEST_MYSQL_USERNAME } else { "erp_test" }
$mysqlPassword = if ($env:ERP_LOCAL_TEST_MYSQL_PASSWORD) { $env:ERP_LOCAL_TEST_MYSQL_PASSWORD } else { "erp_test_password" }
$mysqlRootPassword = if ($env:ERP_LOCAL_TEST_MYSQL_ROOT_PASSWORD) { $env:ERP_LOCAL_TEST_MYSQL_ROOT_PASSWORD } else { "erp_local_test_root_password" }

function Stop-TestMySql {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker rm -f $mysqlContainer *> $null
    }
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker is required. Start Docker Desktop and retry."
    }

    # Docker CLI contexts (for example Colima on macOS) are not automatically
    # visible to Testcontainers. Preserve an explicit value; otherwise export
    # the active CLI context endpoint for Maven/Testcontainers.
    if ([string]::IsNullOrWhiteSpace($env:DOCKER_HOST)) {
        $dockerContext = (& docker context show 2>$null | Select-Object -First 1).Trim()
        if (-not [string]::IsNullOrWhiteSpace($dockerContext)) {
            $dockerHost = (& docker context inspect --format '{{.Endpoints.docker.Host}}' $dockerContext 2>$null | Select-Object -First 1).Trim()
            if (-not [string]::IsNullOrWhiteSpace($dockerHost) -and $dockerHost -ne '<no value>') {
                $env:DOCKER_HOST = $dockerHost
                Write-Host "[test-local] Exported DOCKER_HOST from Docker context $dockerContext`: $env:DOCKER_HOST"
            }
        }
    }

    if ($env:DOCKER_HOST -like 'unix://*' -and [string]::IsNullOrWhiteSpace($env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE)) {
        $env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = '/var/run/docker.sock'
        Write-Host "[test-local] Exported TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=$env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE"
    }

    $null = & docker run --detach --rm `
        --name $mysqlContainer `
        --env "MYSQL_ROOT_PASSWORD=$mysqlRootPassword" `
        --env "MYSQL_DATABASE=$mysqlDatabase" `
        --env "MYSQL_USER=$mysqlUsername" `
        --env "MYSQL_PASSWORD=$mysqlPassword" `
        --publish "127.0.0.1::3306" `
        $mysqlImage `
        --character-set-server=utf8mb4 `
        --collation-server=utf8mb4_0900_ai_ci `
        --default-time-zone=+08:00
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start disposable MySQL container."
    }

    $mysqlPort = (& docker port $mysqlContainer 3306/tcp | Select-Object -First 1).Split(":")[-1]
    if ($mysqlPort -notmatch '^[0-9]+$') {
        throw "Could not determine the disposable MySQL host port."
    }

    Write-Host "[test-local] Waiting for MySQL on 127.0.0.1:$mysqlPort"
    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        & docker exec $mysqlContainer mysqladmin ping -h 127.0.0.1 -uroot "-p$mysqlRootPassword" --silent *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        & docker logs $mysqlContainer
        throw "MySQL did not become ready within 60 seconds."
    }

    $env:ERP_TEST_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/$mysqlDatabase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    $env:ERP_TEST_DATASOURCE_USERNAME = $mysqlUsername
    $env:ERP_TEST_DATASOURCE_PASSWORD = $mysqlPassword

    $goalsPresent = @($MavenArguments | Where-Object { $_ -notmatch '^-' }).Count -gt 0
    $effectiveMavenArguments = [System.Collections.Generic.List[string]]::new()
    foreach ($argument in $MavenArguments) {
        $effectiveMavenArguments.Add($argument)
    }
    if (-not $goalsPresent) {
        $effectiveMavenArguments.Add("test")
    }

    Push-Location $backendRoot
    try {
        $mavenWrapper = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
            Join-Path $backendRoot "mvnw.cmd"
        } else {
            Join-Path $backendRoot "mvnw"
        }
        Write-Host "[test-local] Running Maven against disposable MySQL: $($effectiveMavenArguments -join ' ')"
        & $mavenWrapper -B @effectiveMavenArguments
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
} finally {
    Stop-TestMySql
}
