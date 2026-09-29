@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set GRADLE_OPTS=%GRADLE_OPTS% -Dorg.gradle.appname=%APP_BASE_NAME% -Dorg.gradle.wrapper.url=https\://services.gradle.org/distributions/gradle-8.9-bin.zip

@if defined DEBUG (
    @echo on
) else (
    @echo off
)

@call "%JAVA_HOME%/bin/java" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
:end
