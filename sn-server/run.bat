@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -cp "lib/*" -d build ScanFormat.java SnServer.java
if errorlevel 1 (
  echo Compile failed.
  pause
  exit /b 1
)
rem build (코드), lib/* (slf4j+logback), . (logback.xml 탐색) 을 클래스패스에 포함
java -cp "build;lib/*;." SnServer
pause
