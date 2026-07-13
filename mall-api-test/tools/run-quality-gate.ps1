param(
    [ValidateSet("fast", "slow")]
    [string]$Suite = "fast",

    [string]$Test = "",

    [switch]$SkipRun,

    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
$targetDir = Join-Path $projectDir "target"
$reportsDir = Join-Path $targetDir "surefire-reports"
$metricsPath = Join-Path $targetDir "quality-metrics.json"
$summaryPath = Join-Path $targetDir "quality-summary.md"
$historyPath = Join-Path $targetDir "quality-history.jsonl"
$repoRoot = ""

function New-DirectoryIfMissing {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-SkippedMessage {
    param($TestCase)

    if ($null -eq $TestCase.skipped) {
        return ""
    }

    $message = $TestCase.skipped.message
    if ($null -ne $message -and "$message".Length -gt 0) {
        return "$message"
    }

    return "$($TestCase.skipped.InnerText)"
}

function Get-GitValue {
    param([string[]]$GitArgs)
    if ([string]::IsNullOrWhiteSpace($repoRoot)) {
        return ""
    }
    try {
        $value = & git -C $repoRoot @GitArgs 2>$null
        if ($LASTEXITCODE -eq 0 -and $null -ne $value) {
            return "$value".Trim()
        }
    } catch {
        return ""
    }
    return ""
}

Push-Location $projectDir
try {
    New-DirectoryIfMissing $targetDir
    $repoRootValue = & git -C $projectDir rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -eq 0 -and $null -ne $repoRootValue) {
        $repoRoot = "$repoRootValue".Trim()
    }
    $startedAt = Get-Date
    $mavenExitCode = 0

    if (-not $SkipRun) {
        $mavenArgs = @("clean", "test")
        if ($Suite -eq "slow") {
            $mavenArgs += "-Pslow"
        }
        if ($Test.Trim().Length -gt 0) {
            $mavenArgs += "-Dtest=$Test"
        }

        Write-Host "Running: $Maven $($mavenArgs -join ' ')"
        & $Maven @mavenArgs
        $mavenExitCode = $LASTEXITCODE
    }

    $finishedAt = Get-Date
    $durationSeconds = [Math]::Round(($finishedAt - $startedAt).TotalSeconds, 3)

    $reportFiles = @()
    if (Test-Path -LiteralPath $reportsDir) {
        $reportFiles = Get-ChildItem -LiteralPath $reportsDir -Filter "TEST-*.xml" -File
    }

    $total = 0
    $failures = 0
    $errorCount = 0
    $skipped = 0
    $knownDefectSkipped = 0
    $classes = New-Object System.Collections.Generic.HashSet[string]
    $failedCases = New-Object System.Collections.Generic.List[object]
    $skippedCases = New-Object System.Collections.Generic.List[object]
    $parseErrors = New-Object System.Collections.Generic.List[object]
    $suiteTimeSeconds = 0.0

    foreach ($file in $reportFiles) {
        try {
            [xml]$xml = Get-Content -LiteralPath $file.FullName -Encoding UTF8 -Raw
        } catch {
            $parseErrors.Add([ordered]@{
                file = $file.Name
                message = $_.Exception.Message
            }) | Out-Null
            continue
        }

        $testSuiteNode = $xml.testsuite
        if ($null -eq $testSuiteNode) {
            continue
        }

        $total += [int]$testSuiteNode.tests
        $failures += [int]$testSuiteNode.failures
        $errorCount += [int]$testSuiteNode.errors
        $skipped += [int]$testSuiteNode.skipped
        $suiteTimeSeconds += [double]$testSuiteNode.time

        foreach ($case in $testSuiteNode.testcase) {
            [void]$classes.Add("$($case.classname)")

            if ($null -ne $case.failure -or $null -ne $case.error) {
                $message = ""
                if ($null -ne $case.failure) {
                    $message = "$($case.failure.message)"
                } elseif ($null -ne $case.error) {
                    $message = "$($case.error.message)"
                }
                $failedCases.Add([ordered]@{
                    class = "$($case.classname)"
                    name = "$($case.name)"
                    message = $message
                }) | Out-Null
            }

            if ($null -ne $case.skipped) {
                $skipMessage = Get-SkippedMessage $case
                if ($skipMessage -match "KnownDefect") {
                    $knownDefectSkipped++
                }
                $skippedCases.Add([ordered]@{
                    class = "$($case.classname)"
                    name = "$($case.name)"
                    message = $skipMessage
                }) | Out-Null
            }
        }
    }

    $passed = $total - $failures - $errorCount - $skipped
    if ($passed -lt 0) {
        $passed = 0
    }

    $status = "PASS"
    if ($mavenExitCode -ne 0 -or $failures -gt 0 -or $errorCount -gt 0 -or $reportFiles.Count -eq 0 -or $parseErrors.Count -gt 0) {
        $status = "FAIL"
    }

    $metrics = [ordered]@{}
    $metrics.Add("status", $status)
    $metrics.Add("suite", $Suite)
    $metrics.Add("testFilter", $Test)
    $metrics.Add("generatedAt", (Get-Date).ToString("s"))
    $metrics.Add("runStartedAt", $startedAt.ToString("s"))
    $metrics.Add("runFinishedAt", $finishedAt.ToString("s"))
    $metrics.Add("gitCommit", (Get-GitValue @("rev-parse", "HEAD")))
    $metrics.Add("gitBranch", (Get-GitValue @("rev-parse", "--abbrev-ref", "HEAD")))
    $metrics.Add("projectDir", $projectDir)
    $metrics.Add("mavenExitCode", $mavenExitCode)
    $metrics.Add("durationSeconds", $durationSeconds)
    $metrics.Add("surefireTimeSeconds", [Math]::Round($suiteTimeSeconds, 3))
    $metrics.Add("reportFileCount", $reportFiles.Count)
    $metrics.Add("classCount", $classes.Count)
    $metrics.Add("total", $total)
    $metrics.Add("passed", $passed)
    $metrics.Add("failures", $failures)
    $metrics.Add("errors", $errorCount)
    $metrics.Add("skipped", $skipped)
    $metrics.Add("knownDefectSkipped", $knownDefectSkipped)
    $metrics.Add("parseErrors", [object[]]$parseErrors.ToArray())
    $metrics.Add("failedCases", [object[]]$failedCases.ToArray())
    $metrics.Add("skippedCases", [object[]]$skippedCases.ToArray())

    $metrics | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $metricsPath -Encoding UTF8

    $history = [ordered]@{}
    foreach ($key in @(
        "status", "suite", "testFilter", "generatedAt", "runStartedAt", "runFinishedAt",
        "gitCommit", "gitBranch", "mavenExitCode", "durationSeconds", "surefireTimeSeconds",
        "reportFileCount", "classCount", "total", "passed", "failures", "errors", "skipped",
        "knownDefectSkipped"
    )) {
        $history.Add($key, $metrics[$key])
    }
    ($history | ConvertTo-Json -Compress) | Add-Content -LiteralPath $historyPath -Encoding UTF8

    $failedLines = if ($failedCases.Count -eq 0) {
        "- None"
    } else {
        ($failedCases | ForEach-Object { "- $($_.class).$($_.name): $($_.message)" }) -join "`n"
    }

    $parseErrorLines = if ($parseErrors.Count -eq 0) {
        "- None"
    } else {
        ($parseErrors | ForEach-Object { "- $($_.file): $($_.message)" }) -join "`n"
    }

    $summary = @"
# Mall API Test Quality Summary

| Metric | Value |
|---|---:|
| Status | $status |
| Suite | $Suite |
| Test filter | $Test |
| Git commit | $($metrics["gitCommit"]) |
| Git branch | $($metrics["gitBranch"]) |
| Run started at | $($metrics["runStartedAt"]) |
| Run finished at | $($metrics["runFinishedAt"]) |
| Maven exit code | $mavenExitCode |
| Duration seconds | $durationSeconds |
| Surefire time seconds | $([Math]::Round($suiteTimeSeconds, 3)) |
| Report files | $($reportFiles.Count) |
| Test classes | $($classes.Count) |
| Total | $total |
| Passed | $passed |
| Failures | $failures |
| Errors | $errorCount |
| Skipped | $skipped |
| Known defect skipped | $knownDefectSkipped |
| Parse errors | $($parseErrors.Count) |

## Failed Cases

$failedLines

## Parse Errors

$parseErrorLines

## Artifacts

- Metrics JSON: target/quality-metrics.json
- History JSONL: target/quality-history.jsonl
- Surefire reports: target/surefire-reports/
- Allure results: target/allure-results/
"@

    $summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

    Write-Host "Quality status: $status"
    Write-Host "Metrics: $metricsPath"
    Write-Host "Summary: $summaryPath"

    if ($status -ne "PASS") {
        exit 1
    }
}
finally {
    Pop-Location
}
