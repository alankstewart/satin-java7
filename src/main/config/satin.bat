@echo off

::----------------------------------------------------------------------
:: Satin Startup Script
::----------------------------------------------------------------------

SET CLASS_PATH=lib\*
SET CLASS_PATH=%CLASS_PATH%;etc

java -Xms128m -Xmx512m -Djava.util.logging.config.file=etc\logging.properties -cp %CLASS_PATH% alankstewart.satin.Satin
