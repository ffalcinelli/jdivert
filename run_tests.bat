robocopy C:\jdivert C:\workspace /MIR /XD .git .gradle build .vagrant
cd C:\workspace
taskkill /F /IM java.exe
call .\gradlew test jacocoTestReport --no-daemon
if exist C:\workspace\build\reports\jacoco\test\jacocoTestReport.xml (
    mkdir C:\jdivert\build\reports\jacoco\test
    copy C:\workspace\build\reports\jacoco\test\jacocoTestReport.xml C:\jdivert\build\reports\jacoco\test\jacocoTestReport.xml
)
