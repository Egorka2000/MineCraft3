@echo off
rem tf2-mc expansion pack in one double click, no typing:
rem takes files from the installed Team Fortress 2 and puts the resource pack into the game folder.
rem ASCII only on purpose: cmd.exe breaks on non-ASCII text in .bat files.
rem Extra arguments go to the converter, e.g.: make-pack.bat -GameDir "D:\Games\profile"
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-pack.ps1"
if errorlevel 1 goto failed

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0convert-pack.ps1" -Install %*
if errorlevel 1 goto failed

goto end

:failed
echo.
echo FAILED. See the error above.

:end
if not defined TF2MC_NO_PAUSE pause
endlocal
