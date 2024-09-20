# Pushca simple client (without connection pool support)
Simple Pushca client implementation

To build executable jar: mvn clean compile assembly:single

Deploy to maven central: 
mvn versions:set -DnewVersion=1.0.1
mvn clean -s C:\maven\conf\settings.xml  deploy -P release -DskipTests

[run gnupg agent]

cd c:\Users\bmv2m\AppData\Roaming\gnupg

gpg-connect-agent.exe /bye
