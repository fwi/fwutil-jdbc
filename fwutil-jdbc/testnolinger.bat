@echo off
rem For some reason the Maven surefire plugin takes 10 seconds 
rem between last completed test and showing results.
rem The forkCount option prevents this.
rem To see hanging threads after test complete, start:
rem mvnDebug test
rem and attach a "Remote Java application" to port 8000 via Eclipse> Run> Debug Configurations.   
mvn clean test -DforkCount=0