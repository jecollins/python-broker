Power TAC Python Sample Broker
=======================

The python sample broker is intended to help broker developers by providing a Java-based foundation that interfaces correctly with the Power TAC infrastructure, and makes all incoming messages available through a client API accessible using Py4J. It handles all message types for both wholesale and retail markets. 

Without changing anything, the current version assumes the server is running on localhost, and is not picky about passwords. You can change the server URL by editing the broker.properties file, or by using your own properties file. Passwords are generally ignored outside a tournament environment.

There are really two complete programs here, one written in Java and one in Python, which run in separate processes. By default they communicate with local sockets, but it should be possible to run them on two different machines. The Python program is in control, it starts the Java program and interacts with it through Py4J. In each timeslot, the Java program receives all the messages from the server and puts them in a map, indexed by message type, in which each entry is a list of messages of that type received during the current timeslot. The Python program can compose outgoing messages and send them back to the server by interacting through Py4J.

Import into IDE
---------------

Most developers will presumably want to work with the code using an IDE such as [Eclipse](https://www.eclipse.org/). The Java portion of the package is a maven project, so it works to just do File->Import->Existing Maven Projects and select the python-broker directory (the directory containing the pom.xml file). You may wish to change the "name" attribute in the pom.xml to match the name of your broker. The Python portion is a separate program in the python-src directory.

Run from command line
---------------------

You run the broker by running the Python program. It starts the Java side by providing command-line arguments:

* `--config config-file.properties` specifies an optional properties file that can set username, password, server URL, and other broker properties. If not given, the file broker.properties in the current working directory will be used. 
* `--jms-url tcp://host.name:61616` overrides the JMS URL for the sim server. In a tournament setting this value is supplied by the tournament infrastructure, but this option can be handy for local testing.
* `--log-suffix` if given, takes a string argument that is appended to the string "broker" to name log output files.
* `--no-ntp` if given, tells the broker to not rely on system clock synchronization, but rather to estimate the clock offset between server and broker. Note that this will be an approximation, and may produce errors, but it should at least get the broker into the correct timeslot.
* `--queue-name name` tells the broker to listen on the named queue for messages from the server. This is really only useful for testing, since the queue name defaults to the broker name, and in a tournament situation is provided by the tournament manager upon successful login.
* `--server-queue name` tells the broker the name of the JMS input queue for the server. This is also needed only for testing, because the queue name defaults to 'serverInput' and in a tournament situation is provided by the tournament manager upon successful login.

Note that using the --repeat-count and --repeat-hours features from the Java broker are not currently supported; instead the Python program must be designed to provide the necessary multi-session behavior, re-starting the Java core for each session.

Building the Java core
----------------------

Before you can run the Python broker, you need the Java core to be packaged as a .jar file in the target directory. You to this using Maven, as

`mvn clean package`

Sharing our broker implementations
----------------------------------

Power TAC and other competitive simulations are research tools. A major advantage of the competitive simulation model is the ability to test ideas in a competitive environment. This requires competitors, which means we need to share our broker implementations with each other. Since most teams will be understandably reluctant to share source code, we need a method to share binaries. For mixed Python/Java brokers, this will be done with Docker. Further details pending...
