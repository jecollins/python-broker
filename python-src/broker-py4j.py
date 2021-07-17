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

args = gateway.new_array(gateway.jvm.java.lang.String,2)
args[0] = '--log-suffix'
args[1] = '-py'
print('args:', args[0], args[1])

# broker should now be logging into the server. Once it's logged in, we
# need to retrieve service references
contextManager = envoy.getSpringService('org.powertac.samplebroker.ContextManagerService')
contextManager.logTest("Spring access confirmed");

portfolioManager = envoy.getSpringService('org.powertac.samplebroker.PortfolioManagerService')
marketManager = envoy.getSpringService('org.powertac.samplebroker.MarketManagerService')

timeslotRepo = contextManager.getTimeslotRepo()

print('Starting session')
envoy.startSession(args)
print('Session started')
contextManager.logTest("Start of session");
contextManager.waitForStart()
print('Sim started!')

# we should now be able to pull down the CustomerBootstrapData and MarketBootstrapData
ts = contextManager.waitForTc(0)
print("first timeslot is", ts)
contextMessages = contextManager.getContextMessages()
timeslot = TimeSlotRepo.currentSerialNumber()
print('Timeslot {}, {} context messages', timeslot, len(contextMessages))
