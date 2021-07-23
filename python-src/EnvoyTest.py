#!/usr/bin/python3

# Python broker proof-of-concept using 
# Requires passthrough-broker.jar in the ../target directory
# to provide the Java classpath.

# Test command line for server-distribution:
# mvn -Pcli -Dexec.args="--sim --log-suffix -py --boot-data boot/i1062.xml --config 2week-game.props --brokers PySample"

from py4j.java_gateway import JavaGateway
# open the gateway with our classpath. Requires the broker to be packaged
gateway = JavaGateway().launch_gateway(classpath='../target/passthrough-broker-1.9.0-SNAPSHOT.jar')
# connect to the JVM
envoy = gateway.jvm.org.powertac.samplebroker.Envoy()
#envoy = gateway.entry_point.getEnvoy()

envoy.startDelay()
result = envoy.waitForDelay()
print ('result', result)

print('true', envoy.returnTrue())
print('false', envoy.returnFalse())

