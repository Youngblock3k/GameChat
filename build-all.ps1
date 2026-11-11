# Builds one GyxerChat jar per Minecraft version into .\dist
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-25.0.4.1+1"
New-Item -ItemType Directory -Force dist | Out-Null
foreach ($t in '1.21.11', '26.1', '26.2', '26.3') {
	$p = Start-Process -FilePath .\gradlew.bat -ArgumentList 'clean', 'test', 'shadowJar', "-Ptarget=$t", '--no-daemon', '-q' -NoNewWindow -Wait -PassThru `
		-RedirectStandardOutput "build-$t-out.txt" -RedirectStandardError "build-$t-err.txt"
	if ($p.ExitCode -ne 0) { Get-Content "build-$t-out.txt", "build-$t-err.txt"; throw "build $t failed" }
	Copy-Item "build\libs\GyxerChat-*-mc$t.jar" dist\ -Force
	"built $t"
}
Get-ChildItem dist | Select-Object Name, Length
