param(
    [string]$TemplatePath = ".env.prod.example",
    [string]$OutputPath = ".env.prod",
    [string]$CorsAllowedOrigins = "https://erp.example.com",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Fail {
    param([string]$Message)
    throw "[new-prod-env] $Message"
}

function New-Secret {
    param([int]$Length)
    if ($Length -lt 1) {
        Fail "secret length must be at least 1"
    }

    $alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789_-"
    $builder = [System.Text.StringBuilder]::new()
    for ($i = 0; $i -lt $Length; $i++) {
        $index = [System.Security.Cryptography.RandomNumberGenerator]::GetInt32($alphabet.Length)
        [void]$builder.Append($alphabet[$index])
    }
    return $builder.ToString()
}

function Assert-CorsAllowedOrigins {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        Fail "CorsAllowedOrigins must be a concrete origin list and must not be '*'"
    }

    $origins = $Value.Split(",") | ForEach-Object { $_.Trim() }
    foreach ($origin in $origins) {
        if ([string]::IsNullOrWhiteSpace($origin)) {
            Fail "CorsAllowedOrigins contains an empty origin"
        }
        if ($origin -eq "*") {
            Fail "CorsAllowedOrigins must not contain wildcard origin '*'"
        }

        $uri = $null
        if (-not [System.Uri]::TryCreate($origin, [System.UriKind]::Absolute, [ref]$uri)) {
            Fail "CorsAllowedOrigins contains an invalid origin: $origin"
        }
        if ($uri.Scheme -ne "http" -and $uri.Scheme -ne "https") {
            Fail "CorsAllowedOrigins origin must use http or https: $origin"
        }
        if (-not [string]::IsNullOrWhiteSpace($uri.UserInfo)) {
            Fail "CorsAllowedOrigins origin must not contain user info: $origin"
        }
        if (-not [string]::IsNullOrWhiteSpace($uri.Query) -or -not [string]::IsNullOrWhiteSpace($uri.Fragment)) {
            Fail "CorsAllowedOrigins origin must not contain query string or fragment: $origin"
        }
        if ($uri.AbsolutePath -ne "/") {
            Fail "CorsAllowedOrigins origin must not contain a path: $origin"
        }
    }
}

if (-not (Test-Path -LiteralPath $TemplatePath)) {
    Fail "template file not found: $TemplatePath"
}

if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
    Fail "output file already exists: $OutputPath. Rerun with -Force only after confirming it is safe to overwrite."
}

Assert-CorsAllowedOrigins $CorsAllowedOrigins

$applicationDatabasePassword = New-Secret 40
$replacements = @{
    "MYSQL_ROOT_PASSWORD" = New-Secret 40
    "MYSQL_PASSWORD" = $applicationDatabasePassword
    "ERP_DATASOURCE_PASSWORD" = $applicationDatabasePassword
    "ERP_REDIS_PASSWORD" = New-Secret 40
    "ERP_JWT_SECRET" = New-Secret 72
    "ERP_BOOTSTRAP_ADMIN_PASSWORD" = New-Secret 24
    "ERP_CORS_ALLOWED_ORIGINS" = $CorsAllowedOrigins
}

$lines = foreach ($rawLine in Get-Content -LiteralPath $TemplatePath) {
    $line = $rawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or -not $line.Contains("=")) {
        $rawLine
        continue
    }

    $parts = $rawLine.Split("=", 2)
    $key = $parts[0].Trim()
    if ($replacements.ContainsKey($key)) {
        "$key=$($replacements[$key])"
    } else {
        $rawLine
    }
}

Set-Content -LiteralPath $OutputPath -Value $lines -Encoding UTF8

"Generated $OutputPath from $TemplatePath"
"Review ERP_CORS_ALLOWED_ORIGINS and store secrets in your production secret manager before deployment."
