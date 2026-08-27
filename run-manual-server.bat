@echo off
setlocal EnableExtensions EnableDelayedExpansion

if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17"

if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo Java 17 nao foi encontrado. Configure JAVA_HOME para um JDK 17.
  exit /b 1
)

"%JAVA_HOME%\bin\javac.exe" -version 2>&1 | findstr /B /C:"javac 17." >nul
if errorlevel 1 (
  echo JAVA_HOME deve apontar para o JDK 17. Valor atual: %JAVA_HOME%
  exit /b 1
)

set "ATLAS_EULA_FILE=%~dp0run-manual\eula.txt"
findstr /I /X /C:"eula=true" "%ATLAS_EULA_FILE%" >nul 2>&1
if errorlevel 1 (
  echo.
  echo A EULA do Minecraft ainda nao foi aceita para o servidor manual.
  echo Leia: https://aka.ms/MinecraftEULA
  echo Arquivo: %ATLAS_EULA_FILE%
  echo.
  set /P "ATLAS_EULA_ACCEPT=Digite true para confirmar que concorda, ou Enter para cancelar: "
  if /I not "!ATLAS_EULA_ACCEPT!"=="true" (
    echo EULA nao aceita. O servidor nao foi iniciado.
    exit /b 0
  )
  powershell.exe -NoProfile -Command "$p=$args[0]; if (Test-Path -LiteralPath $p) { $lines=Get-Content -LiteralPath $p; $found=$false; $lines=@($lines | ForEach-Object { if ($_ -match '^eula=') { $found=$true; 'eula=true' } else { $_ } }); if (-not $found) { $lines += 'eula=true' } } else { $lines=@('#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).','eula=true') }; Set-Content -LiteralPath $p -Value $lines -Encoding ASCII" "%ATLAS_EULA_FILE%"
  if errorlevel 1 (
    echo Nao foi possivel atualizar: %ATLAS_EULA_FILE%
    exit /b 1
  )
  echo EULA aceita para o servidor manual.
)

call "%~dp0gradlew.bat" :platform-forge-1.19.2:runManualServer --console=plain
exit /b %ERRORLEVEL%
