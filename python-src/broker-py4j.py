#!/usr/bin/python3

# Python broker proof-of-concept using 
# Requires passthrough-broker.jar in the ../target directory
# to provide the Java classpath.

# Test command line for server-distribution:
# mvn -Pcli -Dexec.args="--sim --log-suffix py --boot-data boot/i1062.xml --config 2week-game.props --brokers PySample"

from py4j.java_gateway import JavaGateway
import time

# open the gateway with our classpath. Requires the broker to be packaged
gateway = JavaGateway().launch_gateway(classpath='../target/passthrough-broker-1.9.0-SNAPSHOT.jar')
# connect to the JVM
envoy = gateway.jvm.org.powertac.samplebroker.Envoy.getInstance()
#envoy = gateway.entry_point.getEnvoy()

args = gateway.new_array(gateway.jvm.java.lang.String,2)
args[0] = '--log-suffix'
args[1] = '-py'
print('args:', args[0], args[1])

# Start the broker
print('Starting session')
envoy.startSession(args)
# broker should now be logging into the server.
print('logging in')

# Retrieve references to the context, portfolio, and market managers
contextManager = envoy.getService('ContextManager')
#contextManager.logTest("Spring access confirmed")
portfolioManager = envoy.getService('PortfolioManager')
marketManager = envoy.getService('MarketManager')
timeslotRepo = contextManager.getTimeslotRepo()
print('Services acquired')

# broker is now logged in, wait for SimStart message to arrive
contextManager.waitForStart()
print('Sim started!')

# we should now be able to pull down the CustomerBootstrapData and MarketBootstrapData
bootstrapMessages = contextManager.getContextMessages()
print('Found {} bootstrap message lists'.format(len(bootstrapMessages)))
for type in bootstrapMessages:
    print('  {}:{}'.format(type, len(bootstrapMessages[type])))

# now we wait for timeslots to finish and retrieve messages
# until one of the message is SimEnd
ts = 0
while not contextManager.isEnded():
    ts = contextManager.waitForTimeslotComplete(ts)
    print("timeslot", ts)
    contextMessages = contextManager.getContextMessages()
    # messages should be a dict
    timeslot = timeslotRepo.currentSerialNumber()
    print('{} context message lists'.format(len(contextMessages)))
    for type in contextMessages:
        print('  {}:{}'.format(type, len(contextMessages[type])))
    portfolioMessages = portfolioManager.getPendingMessageLists()
    if not portfolioMessages is None:
        print('{} portfolio message lists'.format(len(portfolioMessages)))
    marketMessages = marketManager.getPendingMessageLists()
    if not marketMessages is None:
        print('{} market message lists'.format(len(marketMessages)))
print('Sim complete')
