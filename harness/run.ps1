# GyxerChat in-game test for one Minecraft version:  .\run.ps1 -Target 26.2
param([Parameter(Mandatory)][ValidateSet('1.21.11', '26.1', '26.2', '26.3')][string]$Target)
$ErrorActionPreference = 'Stop'
$h = $PSScriptRoot
$root = Split-Path $h -Parent
$ts = Join-Path $root "testservers\$Target"
$out = Join-Path $h "out\$Target"
$enc = New-Object Text.UTF8Encoding $false
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null
$jdk25 = "$env:USERPROFILE\.jdks\jdk-25.0.4.1+1"
$serverJava = if ($Target -eq '1.21.11') { Join-Path (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Filter 'jdk-21*')[0].FullName 'bin\java.exe' } else { "$jdk25\bin\java.exe" }
$botJava = "$jdk25\bin\java.exe"

# build the bots for this version
$env:JAVA_HOME = $jdk25
$p = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList '-p', "`"$h`"", 'classes', 'libs', 'helperJar', "-Ptarget=$Target", '--no-daemon', '-q' -NoNewWindow -Wait -PassThru `
	-RedirectStandardOutput (Join-Path $out 'harness-build.txt') -RedirectStandardError (Join-Path $out 'harness-build.err')
if ($p.ExitCode -ne 0) { Get-Content (Join-Path $out 'harness-build.err'); throw 'harness build failed' }
$hb = Join-Path $h "build-$Target"
$cp = "`"$hb\classes\java\main;$hb\libs-all\*`""

# fresh test server: new world, the OLD v1 config (to test the upgrade), current jars
foreach ($d in 'world', 'world_nether', 'world_the_end', 'plugins\GyxerChat') { $d2 = Join-Path $ts $d; if (Test-Path $d2) { Remove-Item -Recurse -Force $d2 } }
New-Item -ItemType Directory -Force (Join-Path $ts 'plugins\GyxerChat') | Out-Null
Get-ChildItem (Join-Path $ts 'plugins') -Filter 'GyxerChat*.jar' | Remove-Item
Copy-Item (Get-ChildItem (Join-Path $root "dist\GyxerChat-*-mc$Target.jar"))[0].FullName (Join-Path $ts 'plugins\GyxerChat.jar') -Force
Copy-Item (Join-Path $hb 'helper\GyxerTestHelper.jar') (Join-Path $ts 'plugins\GyxerTestHelper.jar') -Force
$old = Get-Content (Join-Path $h 'fixtures\config-v1.yml') -Raw -Encoding UTF8
$old = $old.Replace('interval-seconds: 180', 'interval-seconds: 10').Replace('"&cFound a bug or need help? Please open a ticket!"', '"&cBROADCAST-TEST"')
$old = $old.Replace('  interval-seconds: 300', '  interval-seconds: 3600')
[IO.File]::WriteAllText((Join-Path $ts 'plugins\GyxerChat\config.yml'), $old, $enc)
[IO.File]::WriteAllText((Join-Path $ts 'plugins\GyxerChat\tags.yml'), "tags:`n  OldTag: '&b[OLD]'`n", $enc)
Copy-Item (Join-Path (Split-Path $root -Parent) 'adminkit\testserver\eula.txt') (Join-Path $ts 'eula.txt') -Force
$props = @('online-mode=false', 'enforce-secure-profile=false', 'server-port=25640', 'enable-rcon=true', 'rcon.port=25641', 'rcon.password=gxtest',
	'level-type=minecraft\:flat', 'difficulty=peaceful', 'spawn-protection=0', 'view-distance=3', 'simulation-distance=3', 'generate-structures=false',
	'allow-nether=false', 'max-players=10', 'motd=GyxerChat test', 'spawn-monsters=false', 'pvp=true', 'white-list=false', 'enforce-whitelist=false')
[IO.File]::WriteAllLines((Join-Path $ts 'server.properties'), [string[]]$props, $enc)
Set-Content (Join-Path $ts 'stdin.txt') '' -Encoding ascii

$script:server = $null
$script:logNo = 0
function Start-Server {
	$script:logNo++
	$log = Join-Path $out "server$($script:logNo).log"
	$script:serverLog = $log
	$script:server = Start-Process $serverJava -ArgumentList '-Xms256M', '-Xmx1G', '-jar', 'paper.jar', '--nogui' -WorkingDirectory $ts -NoNewWindow -PassThru `
		-RedirectStandardOutput $log -RedirectStandardError "$log.err" -RedirectStandardInput (Join-Path $ts 'stdin.txt')
	$end = (Get-Date).AddMinutes(6)
	while ((Get-Date) -lt $end) {
		Start-Sleep -Milliseconds 500
		if ($script:server.HasExited) { throw "server exited, see $log" }
		if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Done \(' -Quiet)) { Start-Sleep 2; return }
	}
	throw 'server did not start in time'
}
function Stop-Server {
	Start-Process $botJava -ArgumentList '-cp', $cp, 'gxharness.Stop' -NoNewWindow -Wait -RedirectStandardOutput (Join-Path $out 'stop.log') -RedirectStandardError (Join-Path $out 'stop.err') | Out-Null
	if (-not $script:server.WaitForExit(90000)) { Stop-Process -Id $script:server.Id -Force }
}
function Harness($phase) {
	Start-Process $botJava -ArgumentList '-Xmx384M', '-cp', $cp, 'gxharness.Main', $phase -WorkingDirectory $out -NoNewWindow -Wait `
		-RedirectStandardOutput (Join-Path $out "harness-$phase.log") -RedirectStandardError (Join-Path $out "harness-$phase.err") | Out-Null
}
$extra = New-Object System.Collections.Generic.List[string]
function Note($ok, $text) { $script:extra.Add(($(if ($ok) { 'PASS ' } else { 'FAIL ' }) + $text)) }

Start-Server
$cfgFile = Join-Path $ts 'plugins\GyxerChat\config.yml'
$cfg = Get-Content $cfgFile -Raw -Encoding UTF8
Note ($cfg -match 'config-version: 2' -and $cfg -match 'console-commands:' -and $cfg -match 'cooldown-ms') 'old v1 config: new settings were added'
Note ($cfg -match 'interval-seconds: 10' -and $cfg -match 'BROADCAST-TEST' -and $cfg -match '[''"]1[''"]:') 'old v1 config: the owner''s own values were kept'
Note ($cfg -match 'Message Content Intent') 'old v1 config: comments were kept'
Note (-not (Select-String -Path $script:serverLog -Pattern 'Hooked into PlaceholderAPI' -Quiet)) 'startup banner does not claim PlaceholderAPI when it is missing'
Harness 'main'
Start-Sleep 2
Note (Select-String -Path $script:serverLog -Pattern 'Failed to connect Discord Bot' -Quiet) 'Discord with a bad token: error is logged, server kept running'
Stop-Server
Start-Server
Harness 'restart'
$ign = Get-Content (Join-Path $ts 'plugins\GyxerChat\ignores.yml') -Raw -ErrorAction SilentlyContinue
Note ($ign -match 'ignores:') 'ignores.yml is written right away (not only at shutdown)'
$tags = Get-Content (Join-Path $ts 'plugins\GyxerChat\tags.yml') -Raw
Note ($tags -match 'oldtag:' -and $tags -notmatch 'vip:') 'tags.yml: ids lowercased, deleted tag gone'
Stop-Server

$errors = Get-ChildItem $out -Filter 'server*.log' | ForEach-Object { Get-Content $_.FullName } |
	Where-Object { $_ -match 'ERROR\]|Exception|WARN\].*GyxerChat' -and $_ -match 'gyxerchat|GyxerChat' -and $_ -notmatch 'Failed to connect Discord Bot|Discord|libs\.jda' }
Note (-not $errors) 'no GyxerChat errors or warnings in the server logs'
if ($errors) { $errors | Select-Object -First 25 | ForEach-Object { $extra.Add("   $_") } }
[IO.File]::WriteAllLines((Join-Path $out 'results-checks.txt'), [string[]]$extra, $enc)

Get-ChildItem $out -Filter 'results-*.txt' | ForEach-Object { "== $($_.Name)"; Get-Content $_.FullName -Encoding UTF8 }
