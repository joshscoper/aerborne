[CmdletBinding()]
param(
    [string]$ServerJar = "runtime\paper-test\versions\26.2\paper-26.2.jar",
    [string]$BonusTables = "nerdsmp-paper\src\main\resources\content\drop-tables.yml",
    [string]$Output = "docs\VANILLA_MOB_DROPS_26.2.md"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jarPath = (Resolve-Path -LiteralPath (Join-Path $projectRoot $ServerJar)).Path
$bonusPath = (Resolve-Path -LiteralPath (Join-Path $projectRoot $BonusTables)).Path
$outputPath = Join-Path $projectRoot $Output

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Add-LootMetadata {
    param(
        [object]$Node,
        [System.Collections.Generic.HashSet[string]]$Items,
        [System.Collections.Generic.HashSet[string]]$References,
        [System.Collections.Generic.HashSet[string]]$Conditions,
        [System.Collections.Generic.HashSet[string]]$Functions
    )

    if ($null -eq $Node -or $Node -is [string] -or $Node -is [ValueType]) {
        return
    }
    if ($Node -is [System.Collections.IEnumerable] -and $Node -isnot [System.Management.Automation.PSCustomObject]) {
        foreach ($child in $Node) {
            Add-LootMetadata -Node $child -Items $Items -References $References -Conditions $Conditions -Functions $Functions
        }
        return
    }

    $typeProperty = $Node.PSObject.Properties['type']
    $nameProperty = $Node.PSObject.Properties['name']
    $valueProperty = $Node.PSObject.Properties['value']
    $conditionProperty = $Node.PSObject.Properties['condition']
    $functionProperty = $Node.PSObject.Properties['function']
    if ($null -ne $typeProperty -and $typeProperty.Value -eq 'minecraft:item' -and $null -ne $nameProperty) {
        [void]$Items.Add(($nameProperty.Value -replace '^minecraft:', ''))
    } elseif ($null -ne $typeProperty -and $typeProperty.Value -eq 'minecraft:tag' -and $null -ne $nameProperty) {
        [void]$Items.Add('#' + ($nameProperty.Value -replace '^minecraft:', ''))
    } elseif ($null -ne $typeProperty -and $typeProperty.Value -eq 'minecraft:loot_table' -and $null -ne $valueProperty) {
        [void]$References.Add(($valueProperty.Value -replace '^minecraft:', ''))
    }
    if ($null -ne $conditionProperty) {
        [void]$Conditions.Add(($conditionProperty.Value -replace '^minecraft:', ''))
    }
    if ($null -ne $functionProperty) {
        [void]$Functions.Add(($functionProperty.Value -replace '^minecraft:', ''))
    }
    foreach ($property in $Node.PSObject.Properties) {
        Add-LootMetadata -Node $property.Value -Items $Items -References $References -Conditions $Conditions -Functions $Functions
    }
}

function Read-BonusMap {
    param([string]$Path)

    $map = @{}
    $inTables = $false
    $table = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^tables:\s*$') {
            $inTables = $true
            continue
        }
        if (-not $inTables) {
            continue
        }
        if ($line -match '^  ([a-z0-9-]+):\s*$') {
            $table = $Matches[1]
            continue
        }
        if ($null -ne $table -and $line -match '^    entities:\s*\[([^]]+)\]') {
            foreach ($entity in $Matches[1].Split(',')) {
                $key = $entity.Trim().ToLowerInvariant()
                if (-not $map.ContainsKey($key)) {
                    $map[$key] = [System.Collections.Generic.List[string]]::new()
                }
                $map[$key].Add($table)
            }
        }
    }
    return $map
}

function Join-OrDash {
    param([System.Collections.Generic.HashSet[string]]$Values)
    if ($Values.Count -eq 0) {
        return '—'
    }
    return (($Values | Sort-Object) -join ', ') -replace '\|', '\|'
}

function Get-Sha256Hex {
    param([string]$Path)
    $stream = [System.IO.File]::OpenRead($Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '')
    } finally {
        $sha.Dispose()
        $stream.Dispose()
    }
}

$bonusMap = Read-BonusMap -Path $bonusPath
$hostileIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@(
    'blaze', 'bogged', 'breeze', 'camel_husk', 'cave_spider', 'creaking', 'creeper', 'drowned',
    'elder_guardian', 'ender_dragon', 'enderman', 'endermite', 'evoker', 'ghast', 'giant', 'guardian',
    'hoglin', 'husk', 'illusioner', 'magma_cube', 'parched', 'phantom', 'piglin', 'piglin_brute',
    'pillager', 'ravager', 'shulker', 'silverfish', 'skeleton', 'slime', 'spider', 'stray',
    'sulfur_cube', 'vex', 'vindicator', 'warden', 'witch', 'wither', 'wither_skeleton', 'zoglin',
    'zombie', 'zombie_nautilus', 'zombie_villager', 'zombified_piglin'
) | ForEach-Object { [void]$hostileIds.Add($_) }
$adjustedIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@('zombie', 'husk', 'drowned', 'zombie_villager', 'camel_husk', 'zombie_nautilus', 'zoglin',
    'zombified_piglin', 'witch') | ForEach-Object { [void]$adjustedIds.Add($_) }
$specialNotes = @{
    'ender_dragon' = '12,000 XP on first defeat and 500 XP thereafter; dragon egg/gateway progression is outside the entity loot table.'
    'wither' = '1 nether star and 50 XP are awarded by entity code outside the JSON loot table.'
}
$rows = [System.Collections.Generic.List[string]]::new()
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $allEntityEntries = @($zip.Entries | Where-Object {
        $_.FullName -match '^data/minecraft/loot_table/entities/.+\.json$'
    })
    $rootEntries = @($allEntityEntries | Where-Object {
        $_.FullName -match '^data/minecraft/loot_table/entities/[^/]+\.json$'
    } | Sort-Object FullName)
    $index = 0
    foreach ($entry in $rootEntries) {
        $index++
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $json = $reader.ReadToEnd() | ConvertFrom-Json
        } finally {
            $reader.Dispose()
        }
        $items = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        $references = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        $conditions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        $functions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        Add-LootMetadata -Node $json -Items $items -References $references -Conditions $conditions -Functions $functions

        $mobId = [System.IO.Path]::GetFileNameWithoutExtension($entry.FullName)
        $displayName = (Get-Culture).TextInfo.ToTitleCase(($mobId -replace '_', ' '))
        $rules = [System.Collections.Generic.List[string]]::new()
        if ($conditions.Count -gt 0) {
            $rules.Add('conditions: ' + (($conditions | Sort-Object) -join ', '))
        }
        if ($functions.Count -gt 0) {
            $rules.Add('functions: ' + (($functions | Sort-Object) -join ', '))
        }
        if ($references.Count -gt 0) {
            $rules.Add('references: ' + (($references | Sort-Object) -join ', '))
        }
        $ruleText = if ($rules.Count -eq 0) { '—' } else { ($rules -join '; ') }
        $bonus = if ($bonusMap.ContainsKey($mobId)) { ($bonusMap[$mobId] | Sort-Object) -join ', ' } else { '—' }
        $special = if ($specialNotes.ContainsKey($mobId)) { $specialNotes[$mobId] } else { '—' }
        $hostileReward = if ($hostileIds.Contains($mobId)) {
            'sealed gear + coin purse'
        } else {
            '—'
        }
        $decision = if ($adjustedIds.Contains($mobId)) {
            'Tune vanilla + add bonus'
        } elseif ($hostileIds.Contains($mobId) -or $bonusMap.ContainsKey($mobId)) {
            'Add bonus'
        } else {
            'Keep'
        }
        $rows.Add("| $index | $displayName | ``$mobId`` | $(Join-OrDash $items) | $ruleText | $special | $bonus | $hostileReward | $decision |")
    }
} finally {
    $zip.Dispose()
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# Vanilla mob drop review — Paper 26.2')
$lines.Add('')
$lines.Add('Generated from the loot tables bundled with the pinned `paper-26.2.jar`. Regenerate with `scripts/export-vanilla-mob-drops.ps1` after every Paper upgrade.')
$lines.Add('')
$lines.Add("- Source SHA-256: ``$(Get-Sha256Hex $jarPath)``")
$lines.Add("- Top-level entity tables: $($rows.Count)")
$lines.Add("- Total entity loot assets, including variants/sub-tables: $($allEntityEntries.Count)")
$lines.Add('- Player-killed hostiles roll sealed gear at 0.8% standard / 10% elite / two guaranteed boss rolls, before distance reward scaling.')
$lines.Add('- Coin purses roll at 20% for standard hostiles and are guaranteed for elites and bosses; pickup credits Marks through an idempotent ledger transaction.')
$lines.Add('- Vanilla loot remains enabled except for the narrow junk trims named in `vanilla-adjustments`; family groups are additive rolls from `content/drop-tables.yml`.')
$lines.Add('- “Declared drops” lists direct item/tag entries. The rules column flags conditions, count functions, smelting, Looting, and referenced tables that must be reviewed before changing exact odds.')
$lines.Add('- The sheet intentionally includes technical/entity tables such as Armor Stand, Giant, Illusioner, Mannequin, Player, and Villager so the audit has no silent gaps.')
$lines.Add('')
$lines.Add('| # | Mob | Entity ID | Vanilla declared drops | Vanilla rules / references | Non-table rewards / notes | NerdSMP bonus groups | Hostile rewards | Decision |')
$lines.Add('|---:|---|---|---|---|---|---|---|---|')
$lines.AddRange($rows)
$lines.Add('')
$lines.Add('## Review decisions')
$lines.Add('')
$lines.Add('The Decision column reflects the implemented policy. Exact chances and amounts live in `nerdsmp-paper/src/main/resources/content/drop-tables.yml`; rerun this export after Paper or loot-policy changes.')

$outputDirectory = Split-Path -Parent $outputPath
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$encoding = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($outputPath, $lines, $encoding)
Write-Host "Wrote $($rows.Count) top-level mob tables to $outputPath"
