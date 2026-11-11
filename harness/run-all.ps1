# Builds all jars, then runs the in-game test on every Minecraft version.
$h = $PSScriptRoot
& (Join-Path (Split-Path $h -Parent) 'build-all.ps1')
foreach ($t in '1.21.11', '26.1', '26.2', '26.3') {
	"===== $t"
	try { & (Join-Path $h 'run.ps1') -Target $t } catch { "RUN FAILED: $_" }
}
"===== SUMMARY"
foreach ($t in '1.21.11', '26.1', '26.2', '26.3') {
	$all = Get-ChildItem (Join-Path $h "out\$t") -Filter 'results-*.txt' -ErrorAction SilentlyContinue | ForEach-Object { Get-Content $_.FullName -Encoding UTF8 }
	"$t : $(@($all | Where-Object { $_ -like 'PASS*' }).Count) passed, $(@($all | Where-Object { $_ -like 'FAIL*' }).Count) failed"
}
