@echo off
setlocal

rem === Thu muc goc: noi chua file .bat nay (F:\game\3x116\) ===
set ROOT=%~dp0

rem === Duong dan toi java.exe trong thu muc jre ===
set JAVA_EXE=%ROOT%jre\bin\java.exe

rem === Duong dan toi tool.jar trong thu muc app ===
set JAR_FILE=%ROOT%app\tool.jar

echo Root:  %ROOT%
echo Java:  %JAVA_EXE%
echo Jar:   %JAR_FILE%
echo.

if not exist "%JAVA_EXE%" (
    echo [LOI] Khong tim thay java.exe tai: %JAVA_EXE%
    echo Hay kiem tra lai thu muc "jre" co dung ten va vi tri khong.
    pause
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo [LOI] Khong tim thay tool.jar tai: %JAR_FILE%
    pause
    exit /b 1
)

cd /d "%ROOT%app"
"%JAVA_EXE%" -jar "%JAR_FILE%"

echo.
echo Chuong trinh da dong. Nhan phim bat ky de thoat.
pause
