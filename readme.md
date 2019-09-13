## Download Jars

This project has code to download random jars from Maven central. This was 
mainly used for testing random Java class files against our instrumentor.

To build do:

	mvn clean package

To use do:

	java -jar download-jars-1.0.jar <MB-to-download> [suggestion1 suggestion2 ... ]

For example:

	java -jar download-jars-1.0.jar 10

