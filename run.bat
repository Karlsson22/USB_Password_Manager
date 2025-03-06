@echo off
java --module-path "%JAVA_HOME%\javafx-sdk-17\lib" --add-modules javafx.controls,javafx.fxml -jar password-vault-1.0.0.jar
if errorlevel 1 (
    echo JavaFX not found in JAVA_HOME. Trying to run without JavaFX modules...
    java -jar password-vault-1.0.0.jar
)
pause